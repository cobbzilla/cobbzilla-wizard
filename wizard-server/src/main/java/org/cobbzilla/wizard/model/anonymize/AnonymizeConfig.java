package org.cobbzilla.wizard.model.anonymize;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.SingletonList;
import org.cobbzilla.wizard.model.anon.AnonColumn;
import org.cobbzilla.wizard.model.anon.AnonJsonPath;
import org.cobbzilla.wizard.model.anon.AnonTable;
import org.cobbzilla.wizard.model.anon.AnonType;
import org.hibernate.annotations.Type;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.StreamUtil.loadResourceAsStringOrDie;
import static org.cobbzilla.util.reflect.ReflectionUtil.forName;
import static org.cobbzilla.util.string.StringUtil.camelCaseToSnakeCase;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.isEncryptedType;
import static org.cobbzilla.wizard.model.entityconfig.EntityConfig.ENTITY_FILTER;
import static org.springframework.util.ReflectionUtils.doWithFields;
import static org.springframework.util.ReflectionUtils.doWithMethods;

@Slf4j
public class AnonymizeConfig {

    public static List<AnonTable> disableTransformations(List<AnonTable> tables) {
        for (AnonTable t : tables) {
            for (AnonColumn c : t.getColumns()) c.setType(AnonType.passthru);
        }
        return tables;
    }

    public static List<AnonTable> createAnonTables(List<String> packageList) {
        final List<AnonTable> anonTablesList = new ArrayList<>();
        for (String packageName: packageList) {
            try {
                anonTablesList.addAll(createAnonTables(packageName));
            } catch (Exception e) {
                return die("createAnonTables: "+e, e);
            }
        }
        return anonTablesList;
    }

    private static List<AnonTable> createAnonTables(String packageName) {

        final List<AnonTable> anonTables = new ArrayList<>();
        final ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(ENTITY_FILTER);
        for (BeanDefinition def : scanner.findCandidateComponents(packageName)) {
            final Class<?> clazz = forName(def.getBeanClassName());
            final AnonTable anonTable = createAnonTable(clazz);
            if (anonTable != null) anonTables.add(anonTable);
        }
        return anonTables;
    }

    private static AnonTable createAnonTable(Class clazz) {

        final AnonymizeTable anonymizeTable = (AnonymizeTable) clazz.getAnnotation(AnonymizeTable.class);
        final AnonTable anonTable = new AnonTable().setTable(camelCaseToSnakeCase(clazz.getSimpleName()));
        if (anonymizeTable != null) {
            if (!empty(anonymizeTable.name())) anonTable.setTable(anonymizeTable.name());
            anonTable.setTruncate(anonymizeTable.truncate());
        }

        final Map<String, AnonColumn> columns = new HashMap<>();

        doWithFields(clazz,
                f -> {
                    final AnonymizeType anonType = f.getAnnotation(AnonymizeType.class);
                    final AnonymizeEmbedded anonEmbedded = f.getAnnotation(AnonymizeEmbedded.class);
                    final Type hibernateType = f.getAnnotation(Type.class);
                    final String fieldName = f.getName();
                    final List<AnonColumn> found = anonField(fieldName, anonEmbedded, hibernateType, anonType);
                    maybeAdd(columns, found);
                });

        doWithMethods(clazz,
                m -> {
                    final AnonymizeType anonType = m.getAnnotation(AnonymizeType.class);
                    final AnonymizeEmbedded anonEmbedded = m.getAnnotation(AnonymizeEmbedded.class);
                    final Type hibernateType = m.getAnnotation(Type.class);
                    final String fieldName = m.getName().startsWith("get") ? m.getName().substring(3) : m.getName();
                    final List<AnonColumn> found = anonField(fieldName, anonEmbedded, hibernateType, anonType);
                    maybeAdd(columns, found);
                });

        if (anonymizeTable == null && empty(columns)) return null;

        anonTable.setColumns(columns.values().toArray(new AnonColumn[columns.size()]));
        return anonTable;
    }

    private static void maybeAdd(Map<String, AnonColumn> columns, List<AnonColumn> found) {
        if (!empty(found)) columns.putAll(found.stream().collect(Collectors.toMap(AnonColumn::getName, identity())));
    }

    private static List<AnonColumn> anonField(String fieldName,
                                              AnonymizeEmbedded anonEmbedded,
                                              Type hibernateType,
                                              AnonymizeType anonType) {
        // ensure field name is camel case...
        fieldName = camelCaseToSnakeCase(fieldName);
        if (anonType != null) {
            if (anonEmbedded != null) die("createAnonTable: cannot specify both @AnonymizeType and @AnonymizeEmbedded on the same field");
            final AnonColumn c = anonColumn(fieldName, anonType);
            return c == null ? null : new SingletonList<>(c);

        } else if (anonEmbedded != null) {
            final List<AnonColumn> columns = new ArrayList<>();
            final AnonymizeEmbedded anonymizeEmbedded = anonEmbedded;
            for (AnonymizeType anonymizeType : anonymizeEmbedded.list()) {
                final AnonColumn c = anonColumn(anonymizeType.name(), anonymizeType);
                if (c != null) columns.add(c);
            }
            return empty(columns) ? null : columns;

        } else if (hibernateType != null && isEncryptedType(hibernateType.type())) {
            return new SingletonList<>(new AnonColumn()
                    .setEncrypted(true)
                    .setName(camelCaseToSnakeCase(fieldName))
                    .setType(AnonType.passthru));
        }
        return null;
    }

    private static AnonColumn anonColumn(String name, AnonymizeType anonymizeType) {
        String value = anonymizeType.value();
        if (value.startsWith(CLASSPATH_PREFIX)) {
            value = loadResourceAsStringOrDie(value.substring(CLASSPATH_PREFIX.length()));
        }
        final String[] skip = anonymizeType.skip();

        final AnonColumn anonColumn = new AnonColumn().setName(name)
                .setValue(empty(value) ? null : value)
                .setSkip(empty(skip) ? null : skip);

        if (anonymizeType.encrypted()) anonColumn.setEncrypted(anonymizeType.encrypted());
        if (!empty(anonymizeType.json())) {
            final List<AnonJsonPath> anonJsonPathsList = new ArrayList<>();
            for (AnonymizeJsonPath anonymizeJsonPath : anonymizeType.json()) {
                AnonType anonType = null;
                try { anonType = AnonType.guessType(anonymizeType.type()); } catch (Exception ignored) {
                    log.warn("unrecognized type: "+anonymizeType.type()+", ignoring");
                }
                anonJsonPathsList.add(new AnonJsonPath().setType(anonType)
                        .setPath(anonymizeJsonPath.path()));
            }
            anonColumn.setJson(anonJsonPathsList.toArray(new AnonJsonPath[anonJsonPathsList.size()]));
        }
        try {
            anonColumn.setType(AnonType.guessType(anonymizeType.type()));
        } catch (Exception ignored) {
            log.warn("unrecognized type: "+anonymizeType.type()+", ignoring");
        }

        return anonColumn;
    }

}
