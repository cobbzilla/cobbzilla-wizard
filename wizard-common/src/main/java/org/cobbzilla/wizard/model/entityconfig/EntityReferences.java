package org.cobbzilla.wizard.model.entityconfig;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.Topology;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECForeignKey;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECIndex;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECIndexes;
import org.cobbzilla.wizard.util.ClasspathScanner;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.cobbzilla.util.collection.ArrayUtil.arrayToString;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.string.StringUtil.camelCaseToSnakeCase;

@NoArgsConstructor @Accessors(chain=true) @Slf4j
public class EntityReferences {

    public static final int MAX_PG_NAME_LEN = 63;

    public static final Predicate<Field> FIELD_HAS_FK
            = f -> f.getAnnotation(ECForeignKey.class) != null;

    public static final Predicate<Field> FIELD_HAS_CASCADING_FK
            = f -> f.getAnnotation(ECForeignKey.class) != null && f.getAnnotation(ECForeignKey.class).cascade();

    public static final Predicate<Field> FIELD_HAS_INDEX
            = f -> f.getAnnotation(ECIndex.class) != null;

    public static final Function<Field, Class<? extends Identifiable>> FIELD_TO_FK_CLASS
            = f -> f.getAnnotation(ECForeignKey.class).entity();

    @Getter @Setter private String[] packages;

    public List<Class<? extends Identifiable>> dependencyOrder() {
        // find entity classes
        final List<Class<? extends Identifiable>> classes = new ClasspathScanner<Identifiable>()
                .setPackages(packages)
                .setFilter(EntityConfig.ENTITY_FILTER)
                .scan();
        final Topology<Class<? extends Identifiable>> topology = new Topology<>();
        classes.forEach(c -> topology.addNode(c, getReferencedEntities(c)));
        return topology.sortReversed();
    }

    public static boolean hasForeignKey(Class candidate, Class<? extends Identifiable> entityClass) {
        if (candidate.equals(Object.class)) return false;
        if (Arrays.stream(candidate.getDeclaredFields())
                .filter(EntityReferences.FIELD_HAS_FK)
                .anyMatch(f -> f.getAnnotation(ECForeignKey.class).entity().equals(entityClass))) {
            return true;
        }
        return hasForeignKey(candidate.getSuperclass(), entityClass);
    }

    public static List<Class<? extends Identifiable>> getDependentEntities(Class<? extends Identifiable> entityClass, List<Class<? extends Identifiable>> candidates) {
        return candidates.stream()
                .filter(candidate -> hasForeignKey(candidate, entityClass))
                .collect(Collectors.toList());
    }

    public static Collection<EntityFieldReference> getDependencyRefs(Class<? extends Identifiable> entityClass,
                                                                     List<Class<? extends Identifiable>> candidates) {
        final Set<EntityFieldReference> refs = new LinkedHashSet<>();
        candidates.forEach(dep -> refs.addAll(getDependencyRefs(entityClass, dep, dep, refs)));
        return refs;
    }

    private static Set<EntityFieldReference> getDependencyRefs(Class<? extends Identifiable> entityClass,
                                                               Class dependency,
                                                               Class dependencyOrParent,
                                                               Set<EntityFieldReference> refs) {
        if (dependencyOrParent.equals(Object.class)) return refs;
        Arrays.stream(dependencyOrParent.getDeclaredFields())
                .filter(FIELD_HAS_FK)
                .filter(f -> f.getAnnotation(ECForeignKey.class).entity().equals(entityClass))
                .forEach(f -> refs.add(new EntityFieldReference(dependency.getSimpleName(), f.getName())));
        refs.addAll(getDependencyRefs(entityClass, dependency, dependencyOrParent.getSuperclass(), refs));
        return refs;
    }

    public List<String> generateConstraintSql() { return generateConstraintSql(true); }

    public List<String> generateConstraintSql(boolean includeIndexes) {
        final List<String> constraints = new ArrayList<>();
        new ClasspathScanner<>()
                .setPackages(packages)
                .setFilter(EntityConfig.ENTITY_FILTER)
                .scan()
                .forEach(c -> constraints.addAll(constraintsForClass((Class<? extends Identifiable>) c, includeIndexes)));
        return constraints;
    }

    public static List<Class<? extends Identifiable>> getReferencedEntities(Class clazz) {
        final List<Class<? extends Identifiable>> refs = new ArrayList<>();
        while (!clazz.getName().equals(Object.class.getName())) {
            refs.addAll(Arrays.stream(clazz.getDeclaredFields())
                    .filter(EntityReferences.FIELD_HAS_CASCADING_FK)
                    .map(EntityReferences.FIELD_TO_FK_CLASS)
                    .collect(Collectors.toList()));
            clazz = clazz.getSuperclass();
        }
        return refs;
    }

    private List<String> constraintsForClass(Class<? extends Identifiable> clazz, boolean includeIndexes) {
        final List<String> statements = new ArrayList<>(constraintsForClass(clazz, null, includeIndexes));
        Class parent = clazz.getSuperclass();
        while (!parent.equals(Object.class)) {
            statements.addAll(constraintsForClass(clazz, parent, includeIndexes));
            parent = parent.getSuperclass();
        }
        return statements;
    }

    private List<String> constraintsForClass(Class<? extends Identifiable> clazz, Class parent, boolean includeIndexes) {
        final List<String> statements = new ArrayList<>();
        final Class c = parent == null ? clazz : parent;
        Arrays.stream(c.getDeclaredFields())
                .filter(FIELD_HAS_FK)
                .forEach(f -> statements.addAll(fkField(clazz, parent, f, includeIndexes)));
        if (includeIndexes) {
            Arrays.stream(c.getDeclaredFields())
                    .filter(FIELD_HAS_INDEX)
                    .forEach(f -> statements.add(indexField(clazz, parent, f)));
            final ECIndexes tableIndexes = (ECIndexes) c.getAnnotation(ECIndexes.class);
            if (tableIndexes != null) {
                if (tableIndexes.value().length == 0) {
                    log.warn("ECIndexes(" + c.getName() + ") array of @ECIndex annotations was empty");
                }
                Arrays.stream(tableIndexes.value()).forEach(index -> statements.add(compositeIndex(clazz, parent, index)));
            }
        }

        return statements;
    }

    private String indexField(Class<? extends Identifiable> clazz, Class parent, Field f) {
        final Class c = parent == null ? clazz : parent;
        if (!fieldExists(clazz, f.getName())) {
            return die("ECIndex("+c.getName()+", "+f.getName()+": field does not exist: "+f.getName());
        }
        final ECIndex index = f.getAnnotation(ECIndex.class);
        if (index.of().length != 0) {
            return die("ECIndex("+c.getName()+", "+f.getName()+"): 'of' not allowed for single field index");
        }
        final String tableName = camelCaseToSnakeCase(clazz.getSimpleName());
        final String indexNameSuffix = (parent == null ? "" : "_"+camelCaseToSnakeCase(parent.getSimpleName()));
        final String columnName = camelCaseToSnakeCase(f.getName());
        final String statement = empty(index.statement()) ? null : index.statement();
        String name = empty(index.name()) ? null : index.name();
        final boolean unique = index.unique();
        final String where = empty(index.where()) ? "" : " WHERE "+index.where();

        if (name != null && statement != null) {
            return die("ECIndex("+c.getName()+", "+f.getName()+"): cannot specify both name and statement");
        }
        if (statement != null) return statement;

        String indexName = name != null
                ? name + indexNameSuffix
                : tableName + indexNameSuffix + "_" + (unique ? "uniq" : "idx") + "_" + columnName;
        if (indexName.length() > MAX_PG_NAME_LEN) {
            final String shortName = name != null ? name : tableName + "_" + (unique ? "uniq" : "idx") + "_" + shortName(columnName);
            if (shortName.length() > MAX_PG_NAME_LEN) {
                log.warn("ECIndex("+c.getName()+"): index name '"+indexName+"' was shortened to '"+shortName+"' but will still be truncated");
            }
            indexName = shortName;
        }
        return "CREATE "+(unique ? "UNIQUE" : "")+" INDEX "+indexName+" "+"ON "+tableName+"(" + columnName + ")"+where;
    }

    private String compositeIndex(Class<? extends Identifiable> clazz, Class parent, ECIndex index) {
        final Class c = parent == null ? clazz : parent;
        for (String f : index.of()) {
            if (!fieldExists(clazz, f)) {
                return die("ECIndex("+c.getName()+", "+arrayToString(index.of(), ", ")+": field does not exist: "+f);
            }
        }

        final String tableName = camelCaseToSnakeCase(clazz.getSimpleName());
        final String indexNameSuffix = (parent == null ? "" : "_"+camelCaseToSnakeCase(parent.getSimpleName()));
        final List<String> columnNames = Arrays.stream(index.of())
                .map(StringUtil::camelCaseToSnakeCase)
                .collect(Collectors.toList());
        final String statement = empty(index.statement()) ? null : index.statement();
        String name = empty(index.name()) ? null : index.name();
        final boolean unique = index.unique();
        final String where = empty(index.where()) ? "" : " WHERE "+index.where();

        if (name != null && statement != null) {
            return die("ECIndexes("+c.getName()+"): ECIndex cannot specify both name and statement");
        }
        if (statement != null) return statement;
        String indexName = name != null
                ? name + indexNameSuffix
                : tableName + indexNameSuffix + "_" + (unique ? "uniq" : "idx") + "_" + StringUtil.toString(columnNames, "_");
        if (indexName.length() > MAX_PG_NAME_LEN) {
            final String shortName = name != null ? name : tableName + "_" + (unique ? "uniq" : "idx") + "_" +shortNames(columnNames);
            if (shortName.length() > MAX_PG_NAME_LEN) {
                log.warn("ECIndexes("+c.getName()+"): index name '"+indexName+"' was shortened to '"+shortName+"' but will still be truncated");
            }
            indexName = shortName;
        }
        return "CREATE "+(unique ? "UNIQUE " : "")+"INDEX "+indexName+" ON "+tableName+"("+StringUtil.toString(columnNames, ", ")+")"+where;
    }

    private List<String> fkField(Class<? extends Identifiable> clazz, Class parent, Field f, boolean includeIndexes) {
        final Class c = parent == null ? clazz : parent;
        final ECForeignKey fk = f.getAnnotation(ECForeignKey.class);
        if (!fieldExists(fk.entity(), fk.field())) {
            return die("ECForeignKey("+c.getName()+", "+f.getName()+": referenced field does not exist: class="+fk.entity().getName()+", field="+fk.field());
        }
        final String tableName = camelCaseToSnakeCase(clazz.getSimpleName());
        final String fkNameSuffix = (parent == null ? "" : "_"+camelCaseToSnakeCase(parent.getSimpleName()));
        final String columnName = camelCaseToSnakeCase(f.getName());
        final String refTable = camelCaseToSnakeCase(fk.entity().getSimpleName());
        final String refColumn = camelCaseToSnakeCase(fk.field());
        final List<String> sql = new ArrayList<>();
        String constraintName = tableName + fkNameSuffix + "_fk_" + columnName;
        if (constraintName.length() > MAX_PG_NAME_LEN) {
            String shortName = tableName + fkNameSuffix + "_fk_" + shortName(columnName);
            if (shortName.length() > MAX_PG_NAME_LEN) {
                shortName = tableName + shortName(fkNameSuffix) + "_fk_" + shortName(columnName);
                if (shortName.length() > MAX_PG_NAME_LEN) {
                    log.warn("ECForeignKey(" + c.getName() + "): constraint name '" + constraintName + "' was shortened to '" + shortName + "' but will still be truncated");
                }
            }
            constraintName = shortName;
        }
        sql.add("ALTER TABLE " + tableName + " "
                + "ADD CONSTRAINT " + constraintName + " "
                + "FOREIGN KEY (" + columnName + ") "
                + "REFERENCES " + refTable + "(" + refColumn + ")");
        if (fk.index() && includeIndexes) {
            String indexName = tableName + fkNameSuffix + "_idx_" + columnName;
            if (indexName.length() > MAX_PG_NAME_LEN) {
                String shortName = tableName + fkNameSuffix + "_idx_" + shortName(columnName);
                if (shortName.length() > MAX_PG_NAME_LEN) {
                    shortName = tableName + shortName(fkNameSuffix) + "_idx_" + shortName(columnName);
                    if (shortName.length() > MAX_PG_NAME_LEN) {
                        log.warn("ECIndex(" + c.getName() + "): index name '" + indexName + "' was shortened to '" + shortName + "' but will still be truncated");
                    }
                }
                indexName = shortName;
            }
            sql.add("CREATE INDEX " + indexName + " ON " + tableName + "(" + columnName + ")");
        }
        return sql;
    }

    private boolean fieldExists(Class<?> clazz, String f) {
        try {
            if (clazz.getDeclaredField(f) != null) return true;

            // probably unreachable (NoSuchFieldException always thrown if field not found), but just in case
            return fieldExists(clazz.getSuperclass(), f);

        } catch (NoSuchFieldException e) {
            if (clazz.equals(Object.class)) {
                return false;
            }
            return fieldExists(clazz.getSuperclass(), f);
        }
    }


    private String shortNames(List<String> columnNames) {
        final StringBuilder b = new StringBuilder();
        columnNames.forEach(n -> {
            if (b.length() > 0) b.append("_");
            b.append(shortName(n));
        });
        return b.toString();
    }

    private String shortName (String name) {
        if (name.length() <= 4) return name;
        return name.substring(0, 1) + name.substring(1).replaceAll("[AEIOUaeoiu_]", "");
    }

}
