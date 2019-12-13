package org.cobbzilla.wizard.model.entityconfig;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.util.string.HasLocale;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.cobbzilla.wizard.validation.Validator;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ReflectionUtils;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.string.StringUtil.*;

/**
 * Defines API interactions for an entity.
 * <br/>
 * An EntityConfig describes how to use an API to work with instances of a particular REST resource class.
 * <br/>
 * Typically, an EntityConfig specifies:
 * <ul><li>
 *   The fields contained by the class, including lots of metadata.
 * </li><li>
 *   How to create, read, update and delete instances of the resource class.
 * </li><li>
 *   If the resource class has sub-resources (also known as child resources), there will be sub-EntityConfigs to describe those children
 * </li></ul>
 */
@ToString(of="name") @Slf4j
public class EntityConfig {

    public static final AnnotationTypeFilter ENTITY_FILTER = new AnnotationTypeFilter(Entity.class);

    public static final String URI_CUSTOM = ":custom";
    public static final String URI_NOT_SUPPORTED = ":notSupported";

    /**
     * The name of the Java class for the EntityConfig.
     * Default value: the path to the EntityConfig resource is transformed into a Java class name.
     * For example, the entity config defined in `com/acme/model/User.json` would have a default className of `com.acme.model.User`
     */
    @Getter @Setter private String className;

    /**
     * The name of the entity class. This is typically the same as the file name. This is only required for top-level EntityConfigs,
     * it may be omitted from child resources.
     * Default value: none (required) for root EntityConfigs. For child configs, the default value is what the parent refers to that child as.
     */
    @Getter @Setter private String name;

    @Setter private String displayName;
    /**
     * The display name of the entity.
     * Default value: the value of the `name` field
     * @return The display name of the entity
     */
    public String getDisplayName() { return !empty(displayName) ? displayName : camelCaseToString(name); }

    @Setter private String pluralDisplayName;
    /**
     * The plural display name of the entity.
     * Default value: the value of `displayName` is pluralized, using some basic pluralization rules
     * @return The plural display name of the entity
     */
    public String getPluralDisplayName() { return !empty(pluralDisplayName) ? pluralDisplayName : pluralize(getDisplayName()); }

    /** The API endpoint to list instances of the entity class. This always assumes a GET request. */
    @Getter @Setter private String listUri;

    @Setter private List<String> listFields;

    /**
     * After using the `listUri` to obtain some entities, the `listFields` tells which fields should be
     * "columns" in the resulting data table. Fields not listed in `listFields` will not be shown.
     * Default value: all fields
     * @return a List of field names (keys found in the `fields` map) to use when displaying a list of entities.
     */
    public List<String> getListFields() { return !empty(listFields) ? listFields : getFieldNames(); }

    /**
     * A map of `name` -\> [EntityFieldConfig](EntityFieldConfig.md), with each object describing one field of this EntityConfig.
     * Default value: none (required)
     */
    @Getter @Setter private Map<String, EntityFieldConfig> fields = new LinkedHashMap<>();
    @Setter private List<String> fieldNames;

    /**
     * Get a list of all field names from the `fields` map.
     * @return the keySet of the fields map, as a List
     */
    public List<String> getFieldNames() { return !empty(fieldNames) ? fieldNames : new ArrayList<>(getFields().keySet()); }

    @Setter private EntityFieldConfig parentField;

    /**
     * For child entities, `parentField` indicates which EntityFieldConfig in the `fields` map is the one that
     * refers back to its parent. Default value: if one of the EntityFieldConfigs is a reference field with the
     * special reference value of `:parent`, then this field is used.
     * @return the field within the `fields` map that refers back to the parent entity of this entity.
     */
    public EntityFieldConfig getParentField () {
        if (parentField != null) return parentField;
        for (EntityFieldConfig fieldConfig : fields.values()) {
            if (fieldConfig.isParentReference()) return fieldConfig;
        }
        return null;
    }
    public boolean hasParentField () { return getParentField() != null; }

    private String uriPrefix = "";

    /** The HTTP method to use when creating a new entity. Default value: `PUT` */
    @Getter @Setter private String createMethod = "PUT";
    /** The API endpoint to use when creating a new entity. Default value: none */
    @Getter @Setter private String createUri;

    /** The HTTP method to use when updating an entity. Default value: `POST` */
    @Getter @Setter private String updateMethod = "POST";
    /** The API endpoint to use when updating an entity. Default value: none */
    @Getter @Setter private String updateUri;

    /** The HTTP method to use when searching entities. Default value: `POST` */
    @Getter @Setter private String searchMethod = "POST";
    /** The API endpoint to use when searching entities. Default value: none */
    @Getter @Setter private String searchUri;

    /**
     * After using the `searchUri` to obtain some entities, the `searchFields` tells which fields should be
     * "columns" in the resulting data table. Fields not listed in `searchFields` will not be shown.
     * Default value: the same as listFields
     * @return a List of field names (keys found in the `fields` map) to use when displaying a list of entities.
     */
    @Setter private List<String> searchFields;
    public List<String> getSearchFields() { return !empty(searchFields) ? searchFields : getListFields(); }

    /** The HTTP method to use when deleting an entity. Default value: `DELETE` */
    @Getter @Setter private String deleteMethod = "DELETE";
    @Setter private String deleteUri;
    /** The API endpoint to use when deleting an entity. Default value: Default value: the value of `updateUri` */
    public String getDeleteUri() { return !empty(deleteUri) ? deleteUri : getUpdateUri(); }

    public void addParent(EntityConfig parentConfig) {
        for (Map.Entry<String, EntityFieldConfig> fieldConfig : parentConfig.getFields().entrySet()) {
            if (!this.fields.containsKey(fieldConfig.getKey())) {
                this.fields.put(fieldConfig.getKey(), fieldConfig.getValue());
            }
        }
    }

    /** Describes child resources of the entity. This is a map of EntityConfig name to EntityConfig. */
    @NotNull @Getter @Setter private Map<String, EntityConfig> children = new HashMap<>();
    public boolean hasChildren () { return !children.isEmpty(); }

    private static Class<?> getClassSafe(String className) {
        if (!empty(className)) {
            // Use Java's Class.forName method so we can catch (and ignore) ClassNotFoundException.
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                log.warn("Cannot find class with name " + className + " for entity config", e);
            }
        }

        return null;
    }

    /* -------------------------------------------------- */
    /* ----- Config updates from class annotations: ----- */

    /** Update properties with values from the annotations from the corresponding class. Doesn't override existing
     *  non-empty values!
     */
    public EntityConfig updateWithAnnotations() {
        return updateWithAnnotations(getClassSafe(getClassName()), false);
    }

    /** Update properties with values from the class' annotation. Doesn't override existing non-empty values! */
    public EntityConfig updateWithAnnotations(Class<?> clazz, boolean isRootECCall) {
        if (isRootECCall && clazz == null) throw new NullPointerException("Root class cannot be null");

        String clazzPackageName = null;
        if (clazz != null) {
            final ECType mainECAnnotation = clazz.getAnnotation(ECType.class);
            if (isRootECCall && (mainECAnnotation == null || !mainECAnnotation.root())) {
                throw new IllegalArgumentException(clazz.getName() + " is not marked as entity-config root class");
            }

            clazzPackageName = clazz.getPackage().getName();

            updateWithAnnotation(clazz, clazz.getAnnotation(ECType.class));
            updateWithAnnotation(clazz.getAnnotation(ECTypeList.class));
            updateWithAnnotation(clazz.getAnnotation(ECTypeSearch.class));
            updateWithAnnotation(clazz.getAnnotation(ECTypeCreate.class));
            updateWithAnnotation(clazz.getAnnotation(ECTypeUpdate.class));
            updateWithAnnotation(clazz.getAnnotation(ECTypeDelete.class));
            updateWithAnnotation(clazz, clazz.getAnnotation(ECTypeURIs.class));

            updateWithAnnotation(clazz, clazz.getAnnotation(ECTypeFields.class));
            updateWithAnnotation(clazz, clazz.getAnnotation(ECTypeChildren.class));
        }

        for (Map.Entry<String, EntityConfig> childConfigEntry : getChildren().entrySet()) {
            final EntityConfig childConfig = childConfigEntry.getValue();
            if (empty(childConfig.getClassName()) && clazzPackageName != null) {
                childConfig.setClassName(clazzPackageName + "." + childConfigEntry.getKey());
            }
            childConfig.updateWithAnnotations();
        }

        if (clazz != null) {
            updateWithAnnotation(clazz, clazz.getAnnotation(ECFieldOverwrite.class));
            updateWithAnnotation(clazz, clazz.getAnnotation(ECFieldReferenceOverwrites.class));
        }

        return this;
    }

    /** Update properties with values from the given annotation. Doesn't override existing non-empty values! */
    private EntityConfig updateWithAnnotation(Class<?> clazz, ECType annotation) {
        if (annotation == null) return this;

        if (empty(name)) setName(!empty(annotation.name()) ? annotation.name() : clazz.getSimpleName());
        if (empty(displayName)) setDisplayName(annotation.displayName());
        if (empty(pluralDisplayName)) setPluralDisplayName(annotation.pluralDisplayName());

        return this;
    }

    /** Update properties with values from the given annotation. Doesn't override existing non-empty values! */
    private EntityConfig updateWithAnnotation(ECTypeList annotation) {
        if (annotation == null) return this;

        if (empty(listFields)) setListFields(Arrays.asList(annotation.fields()));
        if (empty(listUri)) setListUri((annotation.uri().startsWith(":") ? "" : uriPrefix) + annotation.uri());

        return this;
    }

    /** Update properties with values from the given annotation. Doesn't override existing non-empty values! */
    private EntityConfig updateWithAnnotation(ECTypeSearch annotation) {
        if (annotation == null) return this;

        if (empty(searchFields)) setSearchFields(Arrays.asList(annotation.fields()));
        if (empty(searchMethod)) setSearchMethod(annotation.method());
        if (empty(searchUri)) setSearchUri((annotation.uri().startsWith(":") ? "" : uriPrefix) + annotation.uri());

        return this;
    }

    /** Update properties with values from the given annotation. Doesn't override existing non-empty values! */
    private EntityConfig updateWithAnnotation(ECTypeCreate annotation) {
        if (annotation == null) return this;

        if (empty(createMethod)) setCreateMethod(annotation.method());
        if (empty(createUri)) setCreateUri((annotation.uri().startsWith(":") ? "" : uriPrefix) + annotation.uri());

        return this;
    }

    /** Update properties with values from the given annotation. Doesn't override existing non-empty values! */
    private EntityConfig updateWithAnnotation(ECTypeUpdate annotation) {
        if (annotation == null) return this;

        if (empty(updateMethod)) setUpdateMethod(annotation.method());
        if (empty(updateUri)) setUpdateUri((annotation.uri().startsWith(":") ? "" : uriPrefix) + annotation.uri());

        return this;
    }

    /** Update properties with values from the given annotation. Doesn't override existing non-empty values! */
    private EntityConfig updateWithAnnotation(ECTypeDelete annotation) {
        if (annotation == null) return this;

        if (empty(deleteMethod)) setDeleteMethod(annotation.method());
        if (empty(deleteUri)) setDeleteUri((annotation.uri().startsWith(":") ? "" : uriPrefix) + annotation.uri());

        return this;
    }

    /** Update properties with values from the given annotation. Doesn't override existing non-empty values! */
    private EntityConfig updateWithAnnotation(Class<?> clazz, ECTypeURIs annotation) {
        if (annotation == null) return this;

        final StringBuilder b = new StringBuilder(this.uriPrefix);
        if (!empty(annotation.baseURI())) {
            final String annotationBase = annotation.baseURI().startsWith(this.uriPrefix)
                    ? annotation.baseURI().substring(this.uriPrefix.length())
                    : annotation.baseURI();
            b.append(annotationBase);
        } else {
            b.append("/").append(pluralize(uncapitalize(clazz.getSimpleName())));
        }
        final String baseUri = b.toString();

        if (annotation.isListDefined()) {
            if (empty(listUri)) setListUri(baseUri);
            if (empty(listFields) && !empty(annotation.listFields())) {
                setListFields(Arrays.asList(annotation.listFields()));
            }
        }
        if (empty(createUri) && annotation.isCreateDefined()) setCreateUri(baseUri);

        String identifiableURI = baseUri + (baseUri.endsWith("/") ? "" : "/") +
                                 "{" + annotation.identifierInURI() + "}";

        if (empty(updateUri) && annotation.isUpdateDefined()) setUpdateUri(identifiableURI);
        if (empty(deleteUri) && annotation.isDeleteDefined()) setDeleteUri(identifiableURI);
        return this;
    }

    /** Update properties with values from the given annotation. Doesn't override existing non-empty values! */
    private EntityConfig updateWithAnnotation(Class<?> clazz, ECTypeFields annotation) {
        if (annotation == null) return this;

        if (!empty(annotation.list())) {
            // initialization of fieldNames according to the fields map (if set)
            if (fieldNames == null) fieldNames = getFieldNames();
            List<String> annotationFieldNames = Arrays.asList(annotation.list());
            // remove duplicates if any ...
            fieldNames.removeAll(annotationFieldNames);
            // ... then add the set list in front of other fields (possibly set in JSON)
            fieldNames.addAll(0, annotationFieldNames);

            if (fields == null) fields = new HashMap<>(fieldNames.size());
            final Set<String> initiallyDefinedFields = new HashSet<>(fields.keySet());
            // The config for fields can be taken (built) bellow first from the class property...
            ReflectionUtils.doWithFields(
                    clazz,
                    field -> updateFieldWithAnnotations(field),
                    field -> fieldNames.contains(field.getName()) && !initiallyDefinedFields.contains(field.getName()));
            // ... and then can be overridden with annotation put over getter method (i.e. overridden getter in
            // a subclass). Of course, all this is done only if the field is not defined in the JSON (which overrides
            // everything here).
            ReflectionUtils.doWithMethods(
                    clazz,
                    method -> updateFieldWithAnnotations(method),
                    method -> {
                        String fieldName;
                        try {
                            fieldName = fieldNameFromAccessor(method);
                        } catch (IllegalArgumentException e) {
                            return false;
                        }
                        // ECField annotation over getter will override entity config only if it is built above (by
                        // previous ReflectionUtils call for properties). So the field was not initially configured, and
                        // ECField annotation exists here.
                        boolean isOverridingAnnotation = !initiallyDefinedFields.contains(fieldName) &&
                                                         method.getAnnotation(ECField.class) != null;
                        // Take this config into consideration either if there's no other, or if the overriding
                        // annotation is set on this getter method:
                        return fieldNames.contains(fieldName) &&
                               (!fields.containsKey(fieldName) || isOverridingAnnotation);
                    });
        } else {
            // if existing JSON-based field names are already set, do nothing more
            // but if those are empty too, then scan the class for any @Column annotations, generate Fields for them
        }
        return this;
    }

    private String fieldNameFromAccessor(AccessibleObject accessor) throws IllegalArgumentException {
        if (accessor instanceof Field) return ((Field) accessor).getName();

        if (accessor instanceof Method) {
            final String methodName = ((Method) accessor).getName();
            if (!methodName.startsWith("get") && !methodName.startsWith("is")) {
                throw new IllegalArgumentException("Not a getter method");
            }
            final String fieldInGetterName = methodName.startsWith("get") ? methodName.substring(3)
                                                                          : methodName.substring(2); // "is..." case
            final String fieldName = uncapitalize(fieldInGetterName);
            if (fieldInGetterName.equals(fieldName)) throw new IllegalArgumentException("Not a true getter method");
            return fieldName;
        }

        throw new IllegalArgumentException("Not a Field not Method");
    }

    private void updateFieldWithAnnotations(AccessibleObject accessor) {
        EntityFieldConfig cfg = buildFieldConfig(accessor);
        if (cfg != null) {
            cfg = updateFieldCfgWithRefAnnotation(cfg, accessor.getAnnotation(ECFieldReference.class));
            try {
                fields.put(fieldNameFromAccessor(accessor), cfg);
            } catch (IllegalArgumentException e) {
                log.warn("Ignoring field accessor because of exception", e);
            }
        }
    }

    private EntityConfig findECChildToUpdate(List<String> fieldPathParts) {
        EntityConfig ecToUpdate = this;
        for (int i = 0; i < fieldPathParts.size() - 1; i++) {
            final String part = fieldPathParts.get(i);
            if (!empty(part)) {
                ecToUpdate = ecToUpdate.getChildren().get(part);
                if (ecToUpdate == null) {
                    log.warn("EC child " + part + " not found for path " + StringUtil.toString(fieldPathParts, "."));
                    return null;
                }
            }
        }
        return ecToUpdate;
    }

    /** Call this method only after all children entity-configs are fully updated. */
    private EntityConfig updateWithAnnotation(Class<?> clazz, ECFieldReferenceOverwrites annotation) {
        final ECFieldReferenceOverwrite[] annotationOverwrites =
                annotation != null ? annotation.value() : clazz.getAnnotationsByType(ECFieldReferenceOverwrite.class);

        for (ECFieldReferenceOverwrite annotationOverwrite : annotationOverwrites) {
            final List<String> fieldPathParts = split(annotationOverwrite.fieldPath(), ".");
            EntityConfig ecToUpdate = findECChildToUpdate(fieldPathParts);
            if (ecToUpdate == null) return this;

            final String fieldName = fieldPathParts.get(fieldPathParts.size() - 1);
            ecToUpdate.fields.put(fieldName, updateFieldCfgWithRefAnnotation(EntityFieldConfig.field(fieldName),
                                                                             annotationOverwrite.fieldDef()));
        }
        return this;
    }

    /** Call this method only after all children entity-configs are fully updated. */
    private EntityConfig updateWithAnnotation(Class<?> clazz, ECFieldOverwrite annotation) {
        if (annotation == null) return this;

        final List<String> fieldPathParts = split(annotation.fieldPath(), ".");
        EntityConfig ecToUpdate = findECChildToUpdate(fieldPathParts);
        if (ecToUpdate == null) return this;

        final String fieldName = fieldPathParts.get(fieldPathParts.size() - 1);
        ecToUpdate.fields.put(fieldName, buildFieldCfgFromAnnotation(annotation.fieldDef()));
        return this;
    }

    /** Update properties with values from the given annotation. Doesn't override existing non-empty values! */
    private EntityConfig updateWithAnnotation(Class<?> clazz, ECTypeChildren annotation) {
        // This annotation (container for repeatable annotations) can be either with a list of those annotations, or
        // the class might be annotations with some number of those repeatable annotations (`@ECTypeChild` in this
        // case). Java itself doesn't allow to have both of there annotation types on a single class, so it's safe to
        // have following processing of those:
        final ECTypeChild[] annotationChildren = annotation != null ? annotation.value()
                                                                    : clazz.getAnnotationsByType(ECTypeChild.class);
        if (empty(annotationChildren)) return this;

        for (ECTypeChild annotationChild : annotationChildren) {
            updateWithAnnotation(clazz, annotationChild);
        }

        if (annotation != null && !empty(annotation.uriPrefix())) {
            children.forEach((childName, childCfg) -> childCfg.uriPrefix = this.uriPrefix + annotation.uriPrefix());
        }

        return this;
    }

    private void updateWithAnnotation(Class<?> clazz, ECTypeChild annotationChild) {
        final Class childClazz = annotationChild.type();

        String childName = annotationChild.name();
        if (empty(childName) && childClazz != null) {
            childName = childClazz.getSimpleName();
        } else {
            log.warn("A child without name nor class added to parent class " + clazz.getName() + " - ignoring it");
            return;
        }

        EntityConfig child = children.get(childName);
        if (child == null) {
            child = new EntityConfig();
            children.put(childName, child);
        }
        if (empty(child.getClassName())) child.setClassName(childClazz.getName());

        if (empty(child.getDisplayName())) child.setDisplayName(annotationChild.displayName());

        // Note that even if `parentField` is not set in `child`, if existing, field (from `fields` list) which is set
        // to contain `reference` to `:parent` will be returned by `getParentField` method above!
        if (child.getParentField() == null) {
            child.parentField = new EntityFieldConfig();

            updateFieldCfgWithRefAnnotation(child.parentField, annotationChild.parentFieldRef());
            if (child.parentField.getReference().getEntity().equals(EntityFieldReference.REF_PARENT)) {
                child.parentField.getReference().setEntity(clazz.getSimpleName());
            }

            // Overriding the existing field with the same name. Note that its current config is not set to be `:parent`
            // reference (as we already checked if this child has parent field above), so overriding it is ok to do
            // here.
            if (child.fields.containsKey(child.parentField.getName())) {
                log.info("Parent field's name " + child.parentField.getName() +
                         " was in fields list. Overriding its config");
            }

            if (empty(child.parentField.getName()) && !empty(annotationChild.backref())) {
                child.parentField.setName(annotationChild.backref());
                // Add parent field as a regular field of reference type `:parent` (not sure how current frontends work,
                // so this is the safest way (note the comment above, so `getParentField will still return the parent
                // field set here).
                child.fields.put(child.parentField.getName(), child.parentField);
            }
        }
    }

    private EntityFieldConfig buildFieldCfgFromAnnotation(ECField fieldAnnotation) {
        EntityFieldConfig cfg = new EntityFieldConfig().setMode(fieldAnnotation.mode()).setType(fieldAnnotation.type());
        if (!empty(fieldAnnotation.name())) cfg.setName(fieldAnnotation.name());
        if (!empty(fieldAnnotation.displayName())) cfg.setDisplayName(fieldAnnotation.displayName());
        if (fieldAnnotation.length() > 0) cfg.setLength(fieldAnnotation.length());
        if (!EntityFieldControl.unset.equals(fieldAnnotation.control())) cfg.setControl(fieldAnnotation.control());
        if (!empty(fieldAnnotation.options())) cfg.setOptions(fieldAnnotation.options());
        if (!empty(fieldAnnotation.emptyDisplayValue())) cfg.setEmptyDisplayValue(fieldAnnotation.emptyDisplayValue());
        if (!empty(fieldAnnotation.objectType())) cfg.setObjectType(fieldAnnotation.objectType());
        return cfg;
    }

    private EntityFieldConfig buildFieldConfig(AccessibleObject accessor) {
        final ECField fieldAnnotation = accessor.getAnnotation(ECField.class);
        if (fieldAnnotation != null) {
            return buildFieldCfgFromAnnotation(fieldAnnotation);
        }

        String fieldName = fieldNameFromAccessor(accessor);
        EntityFieldConfig cfg = EntityFieldConfig.field(fieldName);

        if (!(accessor instanceof Field) && !(accessor instanceof Method)) {
            log.warn("Cannot build Field config for accessor which is not Field or Method");
            return cfg;
        }

        Class<?> fieldType = accessor instanceof Field ? ((Field) accessor).getType()
                                                       : ((Method) accessor).getReturnType();

        if (accessor.isAnnotationPresent(Id.class)) {
            cfg.setMode(EntityFieldMode.readOnly).setControl(EntityFieldControl.hidden);
        }

        if (accessor.isAnnotationPresent(Embedded.class)) {
            cfg.setType(EntityFieldType.embedded).setObjectType(fieldType.getSimpleName());
        }

        if (accessor.isAnnotationPresent(Enumerated.class)) {
            ECEnumSelect enumAnnotation = null;
            if (accessor.isAnnotationPresent(ECEnumSelect.class)) {
                enumAnnotation = accessor.getAnnotation(ECEnumSelect.class);
            } else if (fieldType.isAnnotationPresent(ECEnumSelect.class)) {
                // If annotation of the field is not present, we may use the global one set on the enum class.
                enumAnnotation = fieldType.getAnnotation(ECEnumSelect.class);
            }

            if (enumAnnotation != null) {
                cfg.setControl(EntityFieldControl.select)
                   .setMode(enumAnnotation.mode())
                   .setOptions(enumAnnotation.options());
                if (!empty(enumAnnotation.displayName())) cfg.setDisplayName(enumAnnotation.displayName());
            }
        }

        if (fieldType.equals(boolean.class) || fieldType.equals(Boolean.class)) {
            cfg.setType(EntityFieldType.flag);
        } else if (fieldType.equals(int.class) || fieldType.equals(Integer.class) ||
                fieldType.equals(long.class) || fieldType.equals(Long.class)) {
            cfg.setType(EntityFieldType.integer);
        } else if (fieldType.equals(String.class)) {
            cfg.setType(EntityFieldType.string);
        }

        boolean isFieldTypeWithLength = !EntityFieldType.flag.equals(cfg.getType()) &&
                                        !EntityFieldType.reference.equals(cfg.getType()) &&
                                        !EntityFieldControl.hidden.equals(cfg.getControl()) &&
                                        !EntityFieldControl.select.equals(cfg.getControl()) &&
                                        !EntityFieldControl.multi_select.equals(cfg.getControl());

        Column columnAnnotation = accessor.getAnnotation(Column.class);
        if (columnAnnotation != null) {
            if (empty(columnAnnotation.columnDefinition())) {
                if (isFieldTypeWithLength) cfg.setLength(columnAnnotation.length());
            }
            if (!columnAnnotation.updatable() && !EntityFieldMode.readOnly.equals(cfg.getMode())) {
                cfg.setMode(EntityFieldMode.createOnly);
            }
        }

        if (isFieldTypeWithLength) {
            Size sizeAnnotation = accessor.getAnnotation(Size.class);
            if (sizeAnnotation != null) {
                if (!cfg.hasLength() || cfg.getLength() > sizeAnnotation.max()) cfg.setLength(sizeAnnotation.max());
            }
        }

        return cfg;
    }

    private EntityFieldConfig updateFieldCfgWithRefAnnotation(EntityFieldConfig cfg, ECFieldReference refAnnotation) {
        if (refAnnotation == null) return cfg;

        cfg.setType(EntityFieldType.reference);
        cfg.setMode(refAnnotation.mode());
        if (!empty(refAnnotation.control())) cfg.setControl(EntityFieldControl.create(refAnnotation.control()));
        if (!empty(refAnnotation.options())) cfg.setOptions(refAnnotation.options());

        EntityFieldReference ref = new EntityFieldReference();
        ref.setEntity(refAnnotation.refEntity());
        ref.setField(refAnnotation.refField());
        if (!empty(refAnnotation.refDisplayField())) ref.setDisplayField(refAnnotation.refDisplayField());
        if (!empty(refAnnotation.refFinder())) ref.setFinder(refAnnotation.refFinder());
        if (!empty(refAnnotation.emptyDisplayValue())) cfg.setEmptyDisplayValue(refAnnotation.emptyDisplayValue());
        cfg.setReference(ref);

        return cfg;
    }

    public ValidationResult validate(Validator validator, Object o) {
        ValidationResult validation = null;
        final Locale locale = (o instanceof HasLocale) ? ((HasLocale) o).getLocale() : Locale.getDefault();
        for (EntityFieldConfig field : getFields().values()) {
            final Object value = ReflectionUtil.get(o, field.getName());
            if (value != null) {
                ValidationResult fieldValidation = field.validate(locale, validator, value);
                if (fieldValidation != null) {
                    if (validation == null) validation = new ValidationResult();
                    validation.addAll(fieldValidation);
                }
            }
        }
        return validation;
    }
}
