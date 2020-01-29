package org.cobbzilla.wizard.dao;

import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.entityconfig.annotations.*;
import org.cobbzilla.wizard.model.search.*;

import javax.persistence.Column;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;
import static org.cobbzilla.util.handlebars.HandlebarsUtil.registerUtilityHelpers;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.reflect.ReflectionUtil.*;
import static org.cobbzilla.util.string.StringUtil.getPackagePath;
import static org.cobbzilla.wizard.server.config.PgRestServerConfiguration.dbName;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.isEncryptedField;
import static org.cobbzilla.wizard.model.entityconfig.annotations.ECForeignKeySearchDepth.*;
import static org.cobbzilla.wizard.server.config.PgRestServerConfiguration.safeDbName;

@Slf4j
public class SearchViewContext {

    @Getter(lazy=true) private static final String defaultViewTemplate = stream2string(getPackagePath(AbstractDAO.class)+"/default_search_view.sql.hbs");

    private final Class<? extends Identifiable> clazz;

    @Getter private final String viewName;
    @Getter private final List<String> viewColumns = new ArrayList<>();
    @Getter private final List<String> selectColumns = new ArrayList<>();
    @Getter private final Map<String, String> fromTables = new LinkedHashMap<>();
    @Getter private final Map<String, String> joins = new LinkedHashMap<>();
    public boolean getHasJoins() { return !joins.isEmpty(); }

    @Getter private final String rendered;

    public List<String> getFromClauses () {
        final List<String> froms = new ArrayList<>();
        for (Map.Entry<String, String> t : fromTables.entrySet()) {
            froms.add(t.getKey().equals(t.getValue()) ? t.getKey() : t.getValue() +" AS "+t.getKey());
        }
        return froms;
    }

    @Getter(lazy=true) private static final Handlebars handlebars = initHandlebars();
    private static Handlebars initHandlebars() {
        final Handlebars handlebars = new Handlebars(new HandlebarsUtil(SearchViewContext.class.getName()));
        registerUtilityHelpers(handlebars);
        return handlebars;
    }

    public SearchViewContext(Class<? extends Identifiable> clazz) {
        this.clazz = clazz;
        final String tableName = dbName(this.clazz);
        fromTables.put(tableName, tableName);
        searchFields = initSearchFields();
        viewName = safeDbName(dbName(clazz) + "_search_" + hashOf(viewColumns, selectColumns, fromTables.entrySet(), joins.entrySet()));
        rendered = HandlebarsUtil.apply(getHandlebars(), getDefaultViewTemplate(), toMap(this));
    }

    @Getter private final SqlViewField[] searchFields;

    private SqlViewField[] initSearchFields() {
        final Map<String, SqlViewField> fields = new LinkedHashMap<>();
        final ECSearchDepth mainSearchDepth = clazz.getAnnotation(ECSearchDepth.class);
        final ECForeignKeySearchDepth mainDepth = mainSearchDepth == null ? inherit : mainSearchDepth.fkDepth();
        final Map<String, SqlViewField> finalizedFields = initFields(clazz, "", fields, mainDepth, mainDepth);
        return finalizedFields.values().toArray(new SqlViewField[0]);
    }

    private static final Map<String, SearchBound[]> fieldBounds = new ConcurrentHashMap<>();

    private Map<String, SqlViewField> initFields(Class<? extends Identifiable> entityClass,
                                                 String prefix,
                                                 Map<String, SqlViewField> fields,
                                                 ECForeignKeySearchDepth mainDepth,
                                                 ECForeignKeySearchDepth currentDepth) {
        Class c = entityClass;
        final String entityTable = dbName(entityClass);
        while (!c.equals(Object.class)) {
            final ECSearchDepth classSearchDepth = (ECSearchDepth) c.getAnnotation(ECSearchDepth.class);
            for (Field f : fieldsWithAnnotation(entityClass, ECSearchable.class)) {
                final ECSearchable search = f.getAnnotation(ECSearchable.class);
                final ECForeignKey fk = f.getAnnotation(ECForeignKey.class);

                final String fieldName = dbName(f.getName());
                final String viewFieldName = safeDbName(empty(prefix) ? fieldName : prefix+"_"+fieldName);

                if (fields.containsKey(viewFieldName)) continue;

                final String property = !empty(search.property()) ? search.property() : f.getName();
                final SqlViewFieldSetter set = search.setter().equals(ECSearchable.DefaultSqlViewFieldSetter.class)
                        ? null : instantiate(search.setter());
                final String entity = !empty(search.entity()) ? search.entity() : empty(prefix) ? null : prefix;

                addColumn(viewFieldName, empty(prefix) ? entityTable : prefix, fieldName);

                // calculate search field
                final String sfKey = entityClass.getName() + "." + f.getName();
                SearchBound[] bounds = null;
                try {
                    bounds = fieldBounds.computeIfAbsent(sfKey, k -> ((SqlViewSearchResult) instantiate(entityClass)).searchField(f.getName()).getBounds());
                } catch (Exception e) {
                    log.warn("initFields: error building SearchField for "+entityClass.getSimpleName()+"."+f.getName()+": "+e);
                }

                fields.putIfAbsent(viewFieldName, new SqlViewField(viewFieldName)
                        .setType(entityClass)
                        .fieldType(f.getType())
                        .encrypted(isEncryptedField(f))
                        .filter(search.filter())
                        .property(property)
                        .entity(entity)
                        .setter(set)
                        .setBounds(bounds));

                if (fk != null) {
                    if (!fk.cascade()) continue;
                    if (mainDepth == none) continue;
                    if (currentDepth == none) continue;

                    final Column column = f.getAnnotation(Column.class);
                    final boolean outerJoin = column != null && column.nullable();

                    final ECForeignKeySearchDepth classDepth = classSearchDepth == null ? inherit : classSearchDepth.fkDepth();
                    if (classDepth == none && !(mainDepth == deep || currentDepth == deep)) continue;

                    final ECForeignKeySearchDepth fieldDepth = search.fkDepth();
                    if (fieldDepth == none && !(classDepth == deep || mainDepth == deep || currentDepth == deep)) continue;

                    final String fkTableName = dbName(fk.entity());
                    final String tableAlias = viewFieldName;

                    if (outerJoin) {
                        if (fkTableName.equals(tableAlias)) {
                            joins.putIfAbsent(tableAlias, "LEFT OUTER JOIN " + fkTableName + " ON " + (empty(prefix) ? entityTable : prefix) + "." + fieldName + " = " + tableAlias + ".uuid");
                        } else {
                            joins.putIfAbsent(tableAlias, "LEFT OUTER JOIN " + fkTableName + " AS " + tableAlias + " ON " + (empty(prefix) ? entityTable : prefix) + "." + fieldName + " = " + tableAlias + ".uuid");
                        }
                    } else {
                        if (fkTableName.equals(tableAlias)) {
                            joins.putIfAbsent(tableAlias, "INNER JOIN " + fkTableName + " ON " + (empty(prefix) ? entityTable : prefix) + "." + fieldName + " = " + tableAlias + ".uuid");
                        } else {
                            joins.putIfAbsent(tableAlias, "INNER JOIN " + fkTableName + " AS " + tableAlias + " ON " + (empty(prefix) ? entityTable : prefix) + "." + fieldName + " = " + tableAlias + ".uuid");
                        }
                    }
                    if (mainDepth == deep || currentDepth == deep || classDepth == deep || fieldDepth == deep) {
                        fields.putAll(initFields(fk.entity(), tableAlias, fields, mainDepth, deep));

                    } else if (mainDepth == shallow || currentDepth == shallow || classDepth == shallow || fieldDepth == shallow) {
                        fields.putAll(initFields(fk.entity(), tableAlias, fields, mainDepth, none));

                    } else {
                        fields.putAll(initFields(fk.entity(), tableAlias, fields, mainDepth, currentDepth));
                    }
                }
            }
            c = c.getSuperclass();
        }
        return fields;
    }

    public void addColumn(String viewFieldName, String alias, String fieldName) {
        viewColumns.add(viewFieldName);
        selectColumns.add(alias + "." + fieldName);
    }

}
