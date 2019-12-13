package org.cobbzilla.wizard.util;

import com.github.jknack.handlebars.Handlebars;
import com.opencsv.CSVWriter;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.wizard.model.HasRelatedEntities;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.*;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.toMap;

@Slf4j
public class CsvStreamingOutput implements StreamingOutput {

    private final String[] fields;
    private final Collection rows;
    private String[] header;
    private Handlebars handlebars;

    public CsvStreamingOutput(Collection rows, String[] fields, String[] header, Handlebars handlebars) {
        this.rows = rows;
        this.fields = fields;
        this.header = header;
        this.handlebars = handlebars;
    }

    public CsvStreamingOutput(Collection rows, String[] fields, String[] header) {
        this(rows, fields, header, null);
    }

    public CsvStreamingOutput(Collection rows, String[] fields) {
        this(rows, fields, null);
    }

    @Override public void write(OutputStream out) throws IOException, WebApplicationException {

        if (empty(fields)) die("write: no fields specified");

        @Cleanup final CSVWriter writer = new CSVWriter(new OutputStreamWriter(out));

        final List<Map<String, Object>> data = new ArrayList<>();
        final boolean defaultHeaders = empty(header);
        final Set<String> columns = defaultHeaders ? new HashSet<>() : null;
        for (Object row : rows) {
            final Map<String, Object> map = new HashMap<>();
            for (String field : fields) {
                if (field.startsWith("'") && field.endsWith("'")) {
                    map.put(field, field.substring(1, field.length()-1));

                } else if (handlebars != null && field.contains("[[") && field.contains("]]")) {
                    final Map<String, Object> ctx = toMap(row);
                    if (row instanceof HasRelatedEntities) ctx.putAll(((HasRelatedEntities) row).getRelated());
                    map.put(field, HandlebarsUtil.apply(handlebars, field, ctx, '[', ']'));

                } else {
                    map.put(field, ReflectionUtil.get(row, field, null));
                }
            }
            data.add(map);
            if (defaultHeaders) columns.addAll(map.keySet());
        }

        if (defaultHeaders) header = columns.toArray(new String[columns.size()]);
        writer.writeNext(header); // header row

        for (Map<String, Object> row : data) {
            final String[] line = new String[fields.length];
            for (int i = 0; i < line.length; i++) {
                final String field = fields[i];
                final Object value = row.get(field);
                line[i] = value == null ? "" : String.valueOf(value);
            }
            writer.writeNext(line, false);
        }
    }

}
