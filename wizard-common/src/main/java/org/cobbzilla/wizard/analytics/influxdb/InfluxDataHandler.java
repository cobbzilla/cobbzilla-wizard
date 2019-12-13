package org.cobbzilla.wizard.analytics.influxdb;

import org.cobbzilla.wizard.analytics.AnalyticsData;
import org.cobbzilla.wizard.analytics.AnalyticsHandlerBase;

import java.util.Map;

public class InfluxDataHandler extends AnalyticsHandlerBase {

    @Override public String getWriteUrl() {
        return new StringBuilder(config.getHost()).append(":").append(config.getPort()).append("/write?db=").append(config.getEnv()).append("&u=").append(config.getUsername()).append("&p=").append(config.getPassword()).append("&precision=ms").toString();
    }

    @Override public AnalyticsData newDataPoint(String measurement, Map<String, String> tags, Map<String, String> fields, long time) {
        return new InfluxData(measurement, tags, fields, time);
    }

}
