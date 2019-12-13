package org.cobbzilla.wizard.analytics;

import java.util.Map;

public interface AnalyticsHandler {

    void init(AnalyticsConfiguration config);
    String getWriteUrl();
    AnalyticsData newDataPoint(String ratingService_rate, Map<String, String> tags, Map<String, String> fields, long now);
    int getHttpSuccess();

}
