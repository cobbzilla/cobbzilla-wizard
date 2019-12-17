package org.cobbzilla.wizard.model.entityconfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Cleanup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.InvocationHandler;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.lang.ArrayUtils;
import org.cobbzilla.util.daemon.AwaitResult;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.api.ValidationException;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.NamedEntity;
import org.cobbzilla.wizard.util.RestResponse;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.Await.awaitAll;
import static org.cobbzilla.util.daemon.DaemonThreadFactory.fixedPool;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpMethods.POST;
import static org.cobbzilla.util.http.HttpMethods.PUT;
import static org.cobbzilla.util.http.HttpStatusCodes.NOT_FOUND;
import static org.cobbzilla.util.http.HttpStatusCodes.OK;
import static org.cobbzilla.util.io.FileUtil.dirname;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.forName;
import static org.cobbzilla.util.reflect.ReflectionUtil.getSimpleClass;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.util.string.StringUtil.urlEncode;
import static org.cobbzilla.wizard.model.entityconfig.EntityConfig.URI_NOT_SUPPORTED;
import static org.cobbzilla.wizard.model.entityconfig.EntityFieldReference.REF_PARENT;

@Slf4j
public class ModelSetup {

    public static final String ALLOW_UPDATE_PROPERTY = "_update";
    public static final String PERFORM_SUBST_PROPERTY = "_subst";
    public static final String PERFORM_JSON_SUBST_PROPERTY = "_jsonSubst";

    public static final String[] SPECIAL_PROPERTIES = {
            ALLOW_UPDATE_PROPERTY,
            PERFORM_SUBST_PROPERTY,
            PERFORM_JSON_SUBST_PROPERTY
    };

    public static <T> T[] scrubSpecial(JsonNode json, Class<T> clazz) {
        if (!json.isArray()) return die("scrubSpecialFields: expected JSON array");
        final T[] array = (T[]) Array.newInstance(clazz, json.size());
        for (int i=0; i<json.size(); i++) {
            final ObjectNode object = (ObjectNode) json.get(i);
            for (String field : SPECIAL_PROPERTIES) {
                object.remove(field);
            }
            array[i] = json(json(object), clazz);
        }
        return array;
    }

    // 2 x processorCount, max of 50
    public static int maxConcurrency = Math.min(50, 2 * processorCount());

    public static final long CHILD_TIMEOUT = TimeUnit.MINUTES.toMillis(30);
    static { log.info("ModelSetup: maxConcurrency="+maxConcurrency); }

    public static final Map<Integer, Map<String, Identifiable>> entityCache = new HashMap<>();

    private static boolean isVerify() { return getVerifyLog() != null; }

    private static ModelVerifyLog verifyLog = null;
    public static ModelVerifyLog getVerifyLog () { return verifyLog; }
    public static void setVerifyLog (ModelVerifyLog vlog) { verifyLog = vlog; }

    public static LinkedHashMap<String, String> setupModel(ApiClientBase api,
                                                           String entityConfigsEndpoint,
                                                           String prefix,
                                                           ModelSetupListener listener,
                                                           String runName) throws Exception {
        return setupModel(api, entityConfigsEndpoint, prefix, "manifest", listener, runName);
    }

    public static LinkedHashMap<String, String> setupModel(ApiClientBase api,
                                                           String entityConfigsEndpoint,
                                                           String prefix,
                                                           String manifest,
                                                           ModelSetupListener listener,
                                                           String runName) throws Exception {
        final String[] models = json(stream2string(prefix + manifest + ".json"), String[].class, FULL_MAPPER_ALLOW_COMMENTS);
        final LinkedHashMap<String, String> modelJson = loadModels(prefix, models);
        return setupModel(api, entityConfigsEndpoint, modelJson, new ManifestClasspathResolver(prefix), listener, runName);
    }

    private static LinkedHashMap<String, String> loadModels(String prefix, String[] models) {
        final LinkedHashMap<String, String> modelJson = new LinkedHashMap<>(models.length);
        for (String model : models) {
            final String json = stream2string(prefix + model + ".json");
            try {
                // If the json is an array of strings, treat it like a manifest
                final String[] includes = fromJson(json, String[].class);
                log.debug("loadModels: including manifest: "+model);
                final LinkedHashMap<String, String> includedModels = loadModels(prefix, includes);
                for (Map.Entry<String, String> entry : includedModels.entrySet()) {
                    final String path = entry.getKey();
                    if (modelJson.containsKey(path)) {
                        log.debug("loadModels: already included "+ path +", not including again");
                    } else {
                        log.debug("loadModels: adding included model: "+ path);
                        modelJson.put(path, entry.getValue());
                    }
                }
            } catch (MismatchedInputException e) {
                log.debug("loadModels: including regular model file: "+model);
                modelJson.put(model, json);
            } catch (Exception e) {
                return die("loadModels("+prefix+", "+Arrays.toString(models)+"): "+e);
            }
        }
        return modelJson;
    }

    public static LinkedHashMap<String, String> setupModel(ApiClientBase api,
                                                           String entityConfigsEndpoint,
                                                           LinkedHashMap<String, String> models,
                                                           ModelManifestResolver resolver,
                                                           ModelSetupListener listener,
                                                           String runName) throws Exception {
        return setupModel(api, entityConfigsEndpoint, models, resolver, listener, false, runName);
    }

    public static LinkedHashMap<String, String> setupModel(ApiClientBase api,
                                                           String entityConfigsEndpoint,
                                                           LinkedHashMap<String, String> models,
                                                           ModelManifestResolver resolver,
                                                           ModelSetupListener listener,
                                                           boolean update,
                                                           String runName) throws Exception {
        for (Map.Entry<String, String> model : models.entrySet()) {
            final String modelName = model.getKey();
            final String json = model.getValue();
            if (empty(json)) return die("JSON file not found or empty: "+modelName+".json");

            final String entityType = getEntityTypeFromString(modelName);
            if (entityType.equals("manifest")) {
                final LinkedHashMap<String, String> nested = resolver.buildModel(modelName + ".json");
                setupModel(api, entityConfigsEndpoint, nested, resolver.subResolver(dirname(modelName)), listener, update, runName);

            } else {
                try {
                    setupJson(api, entityConfigsEndpoint, entityType, json, listener, update, runName);
                } catch (Exception e) {
                    log.error("setupModel: api=" + api.getBaseUri() + ", model=" + modelName + ", exception=" + e.getClass().getSimpleName() + ": " + e.getMessage());
                    throw e;
                }
            }
        }
        return models;
    }

    private static Map<String, String> modelHashCache = new HashMap<>();

    public static String modelHash(String prefix, String manifest) {
        final String cacheKey = prefix + "/" + manifest;
        String hash = modelHashCache.get(cacheKey);
        if (hash == null) {
            final String[] models = json(stream2string(prefix + manifest + ".json"), String[].class, FULL_MAPPER_ALLOW_COMMENTS);
            StringBuilder b = new StringBuilder();
            for (String model : models) {
                b.append(stream2string(prefix + model + ".json"));
            }
            hash = sha256_hex(b.toString());
            modelHashCache.put(cacheKey, hash);
        }
        return hash;
    }

    public static void setupJson(ApiClientBase api,
                                 String entityConfigsEndpoint,
                                 String entityType,
                                 String json,
                                 ModelSetupListener listener,
                                 String runName) throws Exception {
        setupJson(api, entityConfigsEndpoint, entityType, json, listener, false, runName);
    }

    public static void setupJson(ApiClientBase api,
                                 String entityConfigsEndpoint,
                                 String entityType,
                                 String json,
                                 ModelSetupListener listener,
                                 boolean update,
                                 String runName) throws Exception {
        if (listener != null) listener.preEntityConfig(entityType);
        final EntityConfig entityConfig = api.get(entityConfigsEndpoint + "/" + entityType, EntityConfig.class);
        if (listener != null) listener.postEntityConfig(entityType, entityConfig);

        final Class<? extends Identifiable> entityClass = forName(entityConfig.getClassName());
        final ModelEntity[] entities = parseEntities(json, entityClass, listener);
        for (ModelEntity entity : entities) {
            final LinkedHashMap<String, Identifiable> context = new LinkedHashMap<>();
            createEntity(api, entityConfig, entity, context, listener, update, runName);
        }
    }

    public static ModelEntity[] parseEntities(String json,
                                              Class<? extends Identifiable> entityClass,
                                              ModelSetupListener listener) {
        final JsonNode[] nodes = jsonWithComments(json, JsonNode[].class);
        final ModelEntity[] entities = new ModelEntity[nodes.length];
        for (int i=0; i<nodes.length; i++) {
            final JsonNode node = nodes[i];
            if (!(node instanceof ObjectNode)) {
                log.error("parseEntities: not an ObjectNode, skipping: "+node);
                continue;
            }
            entities[i] = buildModelEntity((ObjectNode) node, entityClass, listener);
        }
        return entities;
    }

    public static ModelEntity buildModelEntity(ObjectNode node,
                                               Class<? extends Identifiable> entityClass, ModelSetupListener listener) {
        final Enhancer enhancer = new Enhancer();
        enhancer.setInterfaces(new Class[]{ModelEntity.class});
        enhancer.setSuperclass(entityClass);
        enhancer.setCallback(new ModelEntityInvocationHandler(node, entityClass, listener));
        return (ModelEntity) enhancer.create();
    }

    // strip off anything after the first underscore (or period, in case a ".json" file is given)
    public static String getEntityTypeFromString(String entityType) {
        if (entityType.contains("/")) entityType = entityType.substring(entityType.lastIndexOf("/")+1);
        if (entityType.contains("_")) return entityType.substring(0, entityType.indexOf("_"));
        if (entityType.contains(".")) return entityType.substring(0, entityType.indexOf("."));
        return entityType;
    }

    protected static void createEntity(final ApiClientBase api,
                                       EntityConfig entityConfig,
                                       ModelEntity request,
                                       final LinkedHashMap<String, Identifiable> context,
                                       final ModelSetupListener listener,
                                       final String runName) throws Exception {
        createEntity(api, entityConfig, request, context, listener, false, runName);
    }

    protected static void createEntity(final ApiClientBase api,
                                       EntityConfig entityConfig,
                                       ModelEntity request,
                                       final LinkedHashMap<String, Identifiable> context,
                                       final ModelSetupListener listener,
                                       boolean update,
                                       final String runName) throws Exception {
        createEntity(api, entityConfig, request, context, listener, false, false, runName);
    }

    protected static void createEntity(final ApiClientBase api,
                                       EntityConfig entityConfig,
                                       ModelEntity request,
                                       final LinkedHashMap<String, Identifiable> context,
                                       final ModelSetupListener listener,
                                       boolean update,
                                       boolean strict,
                                       final String runName) throws Exception {

        Identifiable entity = request;

        // does it already exist?
        final String entityType = getRawClass(entity.getClass().getSimpleName());
        final String updateUri = entityConfig.getUpdateUri();
        final String logPrefix = "createEntity(" + runName + "): " + entityType + ": ";
        if (updateUri != null && !updateUri.equals(URI_NOT_SUPPORTED)) {
            final String getUri = processUri(context, entity, updateUri);
            if (getUri != null) {
                if (listener != null) listener.preLookup(entity);
                final RestResponse response = api.doGet(getUri);
                if (listener != null) listener.postLookup(entity, request, response);
                final boolean verify = isVerify();
                switch (response.status) {
                    case OK:
                        if (verify && request.hasData(strict)) {
                            entity = buildModelEntity(json(response.json, ObjectNode.class), request.getEntity().getClass(), listener);
                            if (listener != null && ((ModelEntity) entity).performSubstitutions()) {
                                entity = listener.subst(entity);
                            }
                            log.info(logPrefix + " diffing: " + id(entity));
                            if (listener != null && request.performSubstitutions()) {
                                request = listener.subst(request);
                            }
                            getVerifyLog().logDifference(getUri, api, context, entityConfig, entity, request);

                        } else if ((update && request.hasData(strict)) || request.forceUpdate()) {
                            final Identifiable existing = getCached(api, json(response.json, request.getEntity().getClass()));
                            Identifiable toUpdate;
                            if (existing != null) {
                                existing.update(entity);
                                toUpdate = existing;
                            } else {
                                toUpdate = entity;
                            }
                            if (listener != null && ((ModelEntity) entity).performSubstitutions()) {
                                toUpdate = listener.subst(toUpdate);
                            }
                            log.info(logPrefix + " already exists, updating: " + id(toUpdate));
                            entity = update(api, context, entityConfig, toUpdate, listener);
                        } else {
                            log.info(logPrefix+" already exists: "+getUri);
                            entity = json(response.json, request.getEntity().getClass());
                        }
                        break;
                    case NOT_FOUND:
                        if (verify) {
                            getVerifyLog().logCreation(getUri, entity instanceof ModelEntity ? ((ModelEntity) entity).getEntity() : entity);
                        } else {
                            entity = create(api, context, entityConfig, entity, listener, runName);
                        }
                        break;
                    default:
                        die(logPrefix+"error creating " + entityType + ": " + response);
                }
            } else {
                entity = create(api, context, entityConfig, entity, listener, runName);
            }
        } else {
            entity = create(api, context, entityConfig, entity, listener, runName);
        }
        if (entity == null) return;

        addToCache(api, entity);

        // copy children if present in request (they wouldn't be in object returned from server)
        if (entity instanceof ParentEntity) {
            ((ParentEntity) entity).setChildren(((ParentEntity) request).getChildren());
        }

        // create and add to context
        context.put(entityType, entity);

        // check for child objects
        if (entity instanceof ParentEntity) {
            final ParentEntity parent = (ParentEntity) entity;
            if (parent.hasChildren()) {
                // sanity check
                if (!entityConfig.hasChildren()) die("createEntity: input data has children but entity config does not support them: "+entityConfig.getClassName());

                // sanity are there any children provided that are unsupported by entity config?
                final Map<String, EntityConfig> entityChildren = entityConfig.getChildren();
                final Set<String> entityChildTypes = entityChildren.keySet();
                final Map<String, JsonNode[]> allChildren = parent.getChildren();
                for (String childType : allChildren.keySet()) {
                    if (!entityChildTypes.contains(childType)) {
                        die("createEntity: input data has children of type "+childType+" but entity config only supports: "+StringUtil.toString(entityChildTypes));
                    }
                }

                for (String childEntityType : entityChildTypes) {

                    // these are the objects we want to create
                    final JsonNode[] children = allChildren.get(childEntityType);
                    if (children == null || children.length == 0) continue;

                    // this tells us how to create them
                    final EntityConfig childConfig = entityChildren.get(childEntityType);

                    // needed to read/write JSON correctly
                    String childClassName = childConfig.getClassName();
                    if (childClassName == null) childClassName = entity.getClass().getPackage().getName() + "." + childEntityType;
                    final Class<? extends Identifiable> childClass = forName(childClassName);

                    @Cleanup("shutdownNow") final ExecutorService exec = fixedPool(Math.min(children.length, maxConcurrency));
                    final Set<Future<?>> futures = new HashSet<>();
                    for (final JsonNode child : children) {
                        if (!(child instanceof ObjectNode)) {
                            log.error("createEntity: not an ObjectNode: "+child);
                            continue;
                        }
                        futures.add(exec.submit(new CreateEntityJob(api, childConfig, child, childClass, context, listener, update, runName)));
                    }
                    final AwaitResult<?> result = awaitAll(futures, CHILD_TIMEOUT);
                    if (!result.allSucceeded()) {
                        final Map<Future, Exception> failures = result.getFailures();
                        if (!empty(failures)) {
                            final String failureMessages = StringUtil.toString(failures.values().stream().map(Throwable::getMessage).collect(Collectors.toList()), "\n");
                            log.error("createEntity: failures: "+failureMessages);
                        }
                        die("createEntity: "+result);
                    }
                }
            }
        }
    }

    private static void addToCache(ApiClientBase api, Identifiable entity) {
        synchronized (entityCache) {
            Map<String, Identifiable> cache = entityCache.get(api.hashCode());
            if (cache == null) {
                cache = new HashMap<>();
                entityCache.put(api.hashCode(), cache);
            }
            final String key = cacheKey(entity);
            cache.put(key, entity);
        }
    }

    private static Identifiable getCached(ApiClientBase api, Identifiable entity) {
        synchronized (entityCache) {
            final Map<String, Identifiable> cache = entityCache.get(api.hashCode());
            final String key = cacheKey(entity);
            Identifiable cachedEntity = null;
            if (cache != null) cachedEntity = cache.get(key);
            if (cachedEntity != null) return cachedEntity;
            addToCache(api, entity);
            return entity;
        }
    }

    private static String cacheKey(Identifiable entity) {
        if (entity instanceof ModelEntity) entity = ((ModelEntity) entity).getEntity();
        return getRawClass(entity.getClass().getName())+"/"+entity.getUuid();
    }

    protected static <T extends Identifiable> T create(ApiClientBase api,
                                                       LinkedHashMap<String, Identifiable> ctx,
                                                       EntityConfig entityConfig,
                                                       T entity,
                                                       ModelSetupListener listener,
                                                       String runName) throws Exception {
        if (isVerify()) {
            log.info("create: in verify mode, not creating "+getSimpleClass(entity).getSimpleName()+": " + id(entity));
            return entity;
        }

        final String uri = processUri(ctx, entity, entityConfig.getCreateUri());

        // if the entity has a parent, it will want that parent's UUID in that field
        setParentFields(ctx, entityConfig, entity);

        if (listener != null) {
            if (entity instanceof ModelEntity && ((ModelEntity) entity).performSubstitutions()) {
                entity = listener.subst(entity);
            }
            listener.preCreate(entityConfig, entity);
        }
        log.info("create("+runName+"): creating " + entityConfig.getName() + ": "+ id(entity));
        T created;
        try {
            switch (entityConfig.getCreateMethod().toUpperCase()) {
                case PUT:  created = api.put(uri, entity); break;
                case POST: created = api.post(uri, entity); break;
                default: return die("invalid create method: "+entityConfig.getCreateMethod());
            }

        } catch (ValidationException e) {
            // try the get again, did it just appear?
            final String getUri = processUri(ctx, entity, entityConfig.getUpdateUri());
            if (getUri == null) {
                if (entity instanceof ParentEntity && ((ParentEntity) entity).hasChildren()) {
                    return die("create: error creating and cannot check for existence: " + entityConfig.getName());
                } else {
                    log.warn("create: error creating and cannot check for existence: " + entityConfig.getName()+", but has no children, skipping");
                    return null;
                }
            }
            try {
                created = api.get(getUri, (Class<T>) getSimpleClass(entity));
                // we're OK, someone else already created it
            } catch (Exception e2) {
                log.error("error creating: "+entityConfig.getCreateMethod()+": "+e2, e2);
                throw e;
            }

        } catch (Exception e) {
            return die("error creating: "+entityConfig.getCreateMethod()+": "+e, e);
        }

        if (listener != null) listener.postCreate(entityConfig, entity, created);
        return created;
    }

    public static <T extends Identifiable> String id(T entity) {
        if (entity == null) return "null";
        if (entity instanceof NamedEntity) return ((NamedEntity) entity).getName();
        if (entity.getUuid() != null) return entity.getUuid();
        return entity.toString();
    }

    protected static <T extends Identifiable> T update(ApiClientBase api,
                                                       LinkedHashMap<String, Identifiable> ctx,
                                                       EntityConfig entityConfig,
                                                       T entity,
                                                       ModelSetupListener listener) throws Exception {
        if (isVerify()) {
            log.info("update: in verify mode, not updating: " + id(entity));
            return entity;
        }
        final String uri = processUri(ctx, entity, entityConfig.getUpdateUri());

        // if the entity has a parent, it will want that parent's UUID in that field
        setParentFields(ctx, entityConfig, entity);

        if (listener != null) listener.preUpdate(entityConfig, entity);
        final T updated;
        switch (entityConfig.getUpdateMethod().toLowerCase()) {
            case "put":  updated = api.put(uri, entity); break;
            case "post": updated = api.post(uri, entity); break;
            default: return die("invalid update method: "+entityConfig.getCreateMethod());
        }
        if (listener != null) listener.postUpdate(entityConfig, entity, updated);
        return updated;
    }

    private static <T extends Identifiable> void setParentFields(LinkedHashMap<String, Identifiable> ctx, EntityConfig entityConfig, T entity) {
        // if the entity has a parent, it will want that parent's UUID in that field
        if (entityConfig.hasParentField()) {
            final EntityFieldConfig parentField = entityConfig.getParentField();

            String parentFieldName = parentField.getName();
            if (parentFieldName != null) {
                String parentEntityType = parentField.getReference().getEntity();
                if (parentEntityType.equals(REF_PARENT)) parentEntityType = parentFieldName;

                boolean ok = false;
                for (Identifiable candidate : ctx.values()) {
                    if (candidate.getClass().getSimpleName().equalsIgnoreCase(parentEntityType)) {
                        ReflectionUtil.set(entity, parentFieldName, ReflectionUtil.get(candidate, parentField.getReference().getField()));
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    die("create: could not find parent (type=" + parentEntityType + ", field=" + parentFieldName + ") of entity (" + entity.getClass().getSimpleName() + "): " + entity);
                }
            } else {
                log.debug("no parentFieldName found for " + entity.getClass().getSimpleName() + ", not setting");
            }
        }
    }

    private static String processUri(LinkedHashMap<String, Identifiable> ctx, Identifiable entity, String uri) {

        if (entity instanceof ModelEntity) entity = ((ModelEntity) entity).getEntity();

        for (Map.Entry<String, Identifiable> entry : ctx.entrySet()) {
            final String type = getRawClass(entry.getKey());
            final Identifiable value = entry.getValue();
            final Map<String, Object> ctxEntryProps = ReflectionUtil.toMap(value instanceof ModelEntity ? ((ModelEntity) value).getEntity() : value);
            for (String name : ctxEntryProps.keySet()) {
                uri = uri.replace("{" + type + "." + name + "}", urlEncode(ctxEntryProps.get(name).toString()));
            }
        }
        final Map<String, Object> entityProps = ReflectionUtil.toMap(entity);
        for (String name : entityProps.keySet()) {
            if (name.contains("$$")) name = name.substring(0, name.indexOf("$$"));
            uri = uri.replace("{" + name + "}", urlEncode(entityProps.get(name).toString()));
        }
        // if a {uuid} remains, try putting in the name, if we have one
        if (uri.contains("{uuid}") && entityProps.containsKey("name")) {
            uri = uri.replace("{uuid}", urlEncode(entityProps.get("name").toString()));
        }
        if (uri.contains("{uuid}")) {
            log.debug("Could not replace {uuid} found in URL, returning null: "+uri);
            return null;
        }
        if (uri.contains("{")) die("Could not replace all variables in URL: "+uri);
        return uri.startsWith("/") ? uri : "/" + uri;
    }

    private static String getRawClass(String className) {
        return className.contains("$$") ? className.substring(0, className.indexOf("$$")) : className;
    }

    public static void scrubSpecialProperties(JsonNode[] nodes) {
        for (JsonNode n : nodes) scrubSpecialProperties(n);
    }
    public static void scrubSpecialProperties(Collection<JsonNode> nodes) {
        for (JsonNode n : nodes) scrubSpecialProperties(n);
    }

    public static void scrubSpecialProperties(JsonNode node) {
        if (node instanceof ObjectNode) {
            // clear special flags if present
            if (node.has(ALLOW_UPDATE_PROPERTY)) ((ObjectNode) node).remove(ALLOW_UPDATE_PROPERTY);
            if (node.has(PERFORM_SUBST_PROPERTY)) ((ObjectNode) node).remove(PERFORM_SUBST_PROPERTY);
        }
    }

    private static class ModelEntityInvocationHandler implements InvocationHandler {

        @Getter @JsonIgnore private ObjectNode node;
        private final boolean update;
        private final boolean subst;
        private final boolean jsonSubst;
        @Getter private final Identifiable entity;

        public ModelEntityInvocationHandler(ObjectNode node, Class<? extends Identifiable> entityClass, ModelSetupListener listener) {
            this.node = node;
            update = hasSpecialProperty(node, ALLOW_UPDATE_PROPERTY);
            subst = hasSpecialProperty(node, PERFORM_SUBST_PROPERTY);
            jsonSubst = hasSpecialProperty(node, PERFORM_JSON_SUBST_PROPERTY);
            if (listener != null && jsonSubst) node = listener.jsonSubst(node);
            this.entity = json(node, entityClass);
        }

        private boolean hasSpecialProperty(ObjectNode node, String prop) {
            boolean val = node.has(prop) && node.get(prop).booleanValue();
            node.remove(prop);
            return val;
        }

        public boolean hasData(final boolean strict) {
            return IteratorUtils.toList(node.fieldNames()).stream().filter((n) -> !ArrayUtils.contains(entity.excludeUpdateFields(strict), n)).count() > 0;
        }

        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "jsonNode": return node;
                case "updateNode": node = json(json(entity), ObjectNode.class); return null;
                case "forceUpdate": return update;
                case "performSubstitutions": return subst;
                case "getEntity": return entity;
                case "hasData": return hasData((Boolean) args[0]);
                case "equals": return entity.equals(args[0]);
                default:
                    try {
                        return method.invoke(entity, args);
                    } catch (InvocationTargetException e) {
                        log.error("invoke("+method.getName()+"): "+e, e);
                        throw e;
                    }
            }
        }
    }

    @AllArgsConstructor
    private static class CreateEntityJob implements Runnable {
        private final ApiClientBase api;
        private final EntityConfig childConfig;
        private final JsonNode child;
        private final Class<? extends Identifiable> childClass;
        private final LinkedHashMap<String, Identifiable> context;
        private final ModelSetupListener listener;
        private final boolean update;
        private final String runName;

        @Override public void run() {
            try {
                createEntity((ApiClientBase) api.clone(), childConfig, buildModelEntity((ObjectNode) child, childClass, listener), new LinkedHashMap<>(context), listener, update, runName);
            } catch (Exception e) {
                die("run: "+e, e);
            }
        }
    }
}
