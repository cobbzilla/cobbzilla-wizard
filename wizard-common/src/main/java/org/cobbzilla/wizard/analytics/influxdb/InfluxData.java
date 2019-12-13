package org.cobbzilla.wizard.analytics.influxdb;

import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.analytics.AnalyticsDataBase;

import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@Accessors(chain=true) @NoArgsConstructor
public class InfluxData extends AnalyticsDataBase {

    public InfluxData(String measurement, Map<String, String> tags, Map<String, String> fields, long time) {
        super(measurement, tags, fields, time);
    }

    @Override public String buildMessage() {
        final StringBuilder message = new StringBuilder().append(measurement);
        for (Map.Entry<String, String> tag: tags.entrySet()){
            message.append(",").append(tag.getKey()).append("=").append("\"").append(tag.getValue()).append("\"");
        }

        if (!fields.isEmpty()){
            message.append(" ");
            String prefix = "";
            for (Map.Entry<String,String> field: fields.entrySet()) {
                message.append(prefix).append(field.getKey()).append("=\"").append(field.getValue()).append("\"");
                prefix = ",";
            }
        }

        if (!empty(time)) message.append(" ").append(time);
        return message.toString();
    }
}
