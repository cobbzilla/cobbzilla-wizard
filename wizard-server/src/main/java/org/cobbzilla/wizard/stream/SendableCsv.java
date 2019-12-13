package org.cobbzilla.wizard.stream;

import com.github.jknack.handlebars.Handlebars;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.http.HttpContentTypes;
import org.cobbzilla.wizard.model.search.ResultPage;
import org.cobbzilla.wizard.model.search.SqlViewField;

import java.util.Collection;
import java.util.List;

public class SendableCsv extends SendableResource {

    public SendableCsv(String name, Collection rows, String[] fields) {
        this(name, rows, fields, null, null);
    }

    public SendableCsv(String name, Collection rows, String[] fields, String[] header) {
        this(name, rows, fields, header, null);
    }

    public SendableCsv(String name, Collection rows, String[] fields, String[] header, Handlebars handlebars) {
        super(new CsvStreamingOutput(rows, fields, header, handlebars));
        setName(name);
    }

    @Override public String getContentType() { return HttpContentTypes.TEXT_CSV; }
    @Override public Boolean getForceDownload() { return true; }

    public static SendableCsv searchToCSV(ResultPage search, String name, List results, SqlViewField[] searchFields, Handlebars handlebars) {
        final String[] fields;
        final String[] header;
        if (search.hasFieldMappings()) {
            final NameAndValue[] fieldMappings = search.getFieldMappings();
            fields = new String[fieldMappings.length];
            header = new String[fieldMappings.length];
            for (int i=0; i<fieldMappings.length; i++) {
                final String value = fieldMappings[i].getValue();
                boolean found = false;
                for (SqlViewField searchField : searchFields) {
                    if (value.equals(searchField.getName())
                            || (value.contains(".") && value.substring(0, value.indexOf(".")).equals(searchField.getName()))) {
                        if (value.contains(".")) {
                            fields[i] = fieldGetter(searchField) + "." + value.substring(value.indexOf('.') + 1);
                        } else {
                            fields[i] = fieldGetter(searchField);
                        }
                        found = true;
                        break;
                    }
                }
                if (!found) fields[i] = value;
                header[i] = fieldMappings[i].getName();
            }
        } else if (search.getHasFields()) {
            fields = search.getFields();
            header = new String[fields.length];
            for (int i=0; i<fields.length; i++) {
                for (SqlViewField searchField : searchFields) {
                    if (fields[i].equals(searchField.getName())) {
                        fields[i] = fieldGetter(searchField);
                        header[i] = searchField.getName();
                        break;
                    }
                }
            }
        } else {
            header = new String[searchFields.length];
            fields = new String[searchFields.length];
            for (int i=0; i<searchFields.length; i++) {
                fields[i] = fieldGetter(searchFields[i]);
                header[i] = searchFields[i].getName();
            }
        }
        return new SendableCsv(name, results, fields, header, handlebars);
    }

    public static String fieldGetter(SqlViewField field) {
        return field.getType() != null
                ? field.hasEntity() ? "related."+ field.getEntity()+"."+ field.getEntityProperty() : field.getEntityProperty()
                : field.getEntityProperty();
    }

}
