package org.cobbzilla.wizard.model.anon;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.wizard.model.Identifiable.UUID;

@Accessors(chain=true) @ToString(of="table")
public class AnonTable {

    @Getter @Setter private String table;
    @Getter @Setter private String id = UUID;
    @Getter @Setter private AnonColumn[] columns;
    @Getter @Setter private boolean truncate = false;

    @JsonIgnore public List<String> getColumnNames () {
        if (empty(columns)) return null;
        final List<String> names = new ArrayList<>();
        for (AnonColumn column : columns) names.add(column.getName());
        return names;
    }

    public static AnonTable table(String table, AnonColumn... columns) {
        return new AnonTable().setTable(table).setColumns(columns);
    }

    public String sqlSelect() {
        if (empty(columns)) return null;
        final StringBuilder b = new StringBuilder();
        for (AnonColumn col : columns) {
            if (b.length() > 0) b.append(", ");
            b.append(col.getName());
        }
        return "SELECT "+ getId()+", " + b.toString() + " FROM " + table;
    }

    public String sqlUpdate() {
        if (isTruncate()) {
            return "TRUNCATE TABLE "+table;

        } else {
            final StringBuilder b = new StringBuilder();
            for (AnonColumn col : columns) {
                if (b.length() > 0) b.append(", ");
                b.append(col.getName()).append(" = ?");
            }
            return "UPDATE " + table + " SET " + b.toString() + " WHERE "+ getId()+" = ?";
        }
    }

    public void retainColumns(Set<String> retain) {
        if (empty(columns)) return;
        int removed = 0;
        for (int i=0; i<columns.length; i++) {
            if (!retain.contains(columns[i].getName())) {
                columns[i] = null;
                removed++;
            }
        }
        final AnonColumn[] newColumns = new AnonColumn[columns.length-removed];
        int newIndex = 0;
        for (AnonColumn column : columns) {
            if (column == null) continue;
            newColumns[newIndex++] = column;
        }
        columns = newColumns;
    }

    public void merge(AnonTable t) {
        if (t.isTruncate()) setTruncate(t.truncate);


        if (!empty(t.getColumns())) {
            for (AnonColumn c : t.getColumns()) {
                final AnonColumn existingColumn = getColumn(c.getName());
                if (existingColumn == null) {
                    columns = ArrayUtil.append(columns, c);
                } else {
                    existingColumn.merge(c);
                }
            }
        }
    }

    private AnonColumn getColumn(String name) {
        if (empty(getColumns())) return null;
        for (AnonColumn c : getColumns()) if (c.getName().equals(name)) return c;
        return null;
    }
}
