package org.cobbzilla.wizard.model.entityconfig;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.ArrayUtils;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.model.ExcludeUpdates;
import org.cobbzilla.wizard.model.Identifiable;

import java.io.File;
import java.util.*;

import static org.cobbzilla.util.collection.FieldTransformer.TO_NAME;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.io.FileUtil.toFileOrDie;
import static org.cobbzilla.util.io.StreamUtil.loadResourceAsStringOrDie;
import static org.cobbzilla.util.json.JsonUtil.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.*;
import static org.cobbzilla.wizard.model.Identifiable.UUID;
import static org.cobbzilla.wizard.model.Identifiable.CTIME;
import static org.cobbzilla.wizard.model.Identifiable.MTIME;
import static org.cobbzilla.wizard.model.entityconfig.ModelSetup.id;

@Slf4j
public class StandardModelVerifyLog implements ModelVerifyLog {

    public static final String BOLD_RIGHT_ARROW = " <b>&#8594;</b> ";

    @Getter @Setter private File verifyLogFile;
    @Getter @Setter private Handlebars handlebars;
    @Getter @Setter private boolean strict;
    @Getter private final List<ModelDiffEntry> diffs = new ArrayList<>();

    public boolean enableTextDiffs () { return false; }

    public StandardModelVerifyLog (File f, Handlebars h, boolean isStrict) {
        verifyLogFile = f;
        handlebars = h;
        strict = isStrict;
    }

    @Override public void startLog() {}

    @Override public void endLog() {
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put("diffs", diffs);
        toFileOrDie(verifyLogFile, HandlebarsUtil.apply(handlebars, loadResourceAsStringOrDie(ModelVerifyLog.HTML_TEMPLATE), ctx));
    }

    @Override public void logDifference(String uri, ApiClientBase api, Map<String, Identifiable> context, EntityConfig entityConfig, Identifiable existing, Identifiable entity) {
        if (!(entity instanceof ModelEntity)) {
            die("logDifference: not a ModelEntity: " + id(entity) + " (is a " + entity.getClass().getName() + ")");
        }
        final ModelEntity request = (ModelEntity) entity;
        final ObjectNode requestNode = request.jsonNode();

        final String existingJson = toBasicJson(existing);
        final String requestJson = toBasicJson(request);

        final ModelDiffEntry diffEntry = new ModelDiffEntry(uri);
        if (!existingJson.equals(requestJson)) {
            final List<String> deltas = new ArrayList<>();
            calculateDiff(api, context, entityConfig, requestNode, existing, deltas);
            if (empty(deltas) && enableTextDiffs()) {
                diffEntry.setJsonDiff(StringUtil.diff(existingJson, requestJson, null));
            } else {
                diffEntry.setDeltas(deltas);
            }
        }
        if (!diffEntry.isEmpty()) {
            synchronized (diffs) {
                diffs.add(diffEntry);
            }
        }
    }

    private String toBasicJson(Identifiable thing) {
        final Identifiable copy = instantiate((Class<? extends Identifiable>) ReflectionUtil.getSimpleClass(thing));
        copy(copy, thing, null, EXCLUDED);
        for (String f : getExcludedFields()) {
            try {
                ReflectionUtil.set(copy, f, null);
            } catch (Exception ignored) {}
        }
        return json(copy instanceof ModelEntity ? ((ModelEntity) copy).getEntity() : copy);
    }

    @Override public void logCreation(String uri, Identifiable entity) {
        diffs.add(new ModelDiffEntry(uri).setCreateEntity(entity));
    }

    private void calculateDiff(ApiClientBase api, Map<String, Identifiable> context, EntityConfig entityConfig, ObjectNode requestNode, Object existing, List<String> deltas) {
        final Object modelRequest = aware(api, context, json(requestNode, forName(entityConfig.getClassName())));
        existing = aware(api, context, existing);

        final Set<String> fields;
        if (modelRequest instanceof Identifiable) {
            fields = TO_NAME.collectSet(entityConfig.getFields().values());
            // remove fields that we should ignore for update purposes
            fields.removeIf((f) -> ArrayUtils.contains(((Identifiable) modelRequest).excludeUpdateFields(strict), f));
        } else {
            final Set<String> requestFields = ReflectionUtil.toMap(modelRequest).keySet();
            final Set<String> existingFields = ReflectionUtil.toMap(existing).keySet();
            requestFields.addAll(existingFields);
            fields = requestFields;
            if (modelRequest instanceof ExcludeUpdates) fields.removeIf((f) -> ArrayUtils.contains(((ExcludeUpdates) modelRequest).excludeUpdateFields(), f));
        }

        for (String fieldName : fields) {
            if (getExcludedFields().contains(fieldName)) continue; // skip children/entity fields
            final Object fieldValue;
            try {
                fieldValue = ReflectionUtil.get(modelRequest, fieldName);
            } catch (Exception e) {
                log.info("calculateDiff: fieldValue could not be found ("+fieldName+"), maybe no getter? skipping: "+e);
                continue;
            }
            Object requestValue = aware(api, context, fieldValue);
            Object existingValue;
            try {
                if (existing instanceof ObjectNode) {
                    existingValue = json(((ObjectNode) existing).get(fieldName), ReflectionUtil.getterType(requestValue, fieldName));
                } else {
                    existingValue = aware(api, context, ReflectionUtil.get(existing, fieldName));
                }
            } catch (Exception e) {
                log.warn("calculateDiff: error fetching " + fieldName + ": " + e);
                continue;
            }

            if (empty(requestValue)) {
                if (empty(existingValue)) continue; // both are nothing
                deltas.add(new StringBuilder().append(fieldName).append(": ").append(json_html(existingValue, NOTNULL_MAPPER_ALLOW_EMPTY)).append(BOLD_RIGHT_ARROW).append(" [absent]").toString());

            } else if (empty(existingValue)) {
                deltas.add(new StringBuilder().append(fieldName).append(": [absent] ").append(BOLD_RIGHT_ARROW).append(json_html(requestValue, NOTNULL_MAPPER_ALLOW_EMPTY)).toString());

            } else if (!json(existingValue, NOTNULL_MAPPER_ALLOW_EMPTY).equals(json(requestValue, NOTNULL_MAPPER_ALLOW_EMPTY))) {
                deltas.add(new StringBuilder().append(fieldName).append(": ").append(json_html(existingValue)).append("<br/>").append(BOLD_RIGHT_ARROW).append(json_html(requestValue)).toString());

            } else {
                // nothing changed
                continue;
            }
        }
    }

    private Object aware(ApiClientBase api, Map<String, Identifiable> context, Object o) {
        try {
            return (o != null && (o instanceof VerifyLogAware) ? ((VerifyLogAware) o).beforeDiff(o, context, api) : o);
        } catch (Exception e) {
            return die("aware("+o+"): "+e, e);
        }
    }

    protected static final String[] EXCLUDED = {UUID, "children", CTIME, "ctimeAge", MTIME, "mtimeAge", "entity"};
    protected static final Set<String> EXCLUDED_FIELDS = new HashSet<>(Arrays.asList(EXCLUDED));
    protected Set<String> getExcludedFields() { return EXCLUDED_FIELDS; }

}
