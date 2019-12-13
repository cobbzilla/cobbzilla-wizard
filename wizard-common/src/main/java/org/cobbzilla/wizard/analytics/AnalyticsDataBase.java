package org.cobbzilla.wizard.analytics;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor @AllArgsConstructor
public abstract class AnalyticsDataBase implements AnalyticsData {

    @Getter @Setter protected String measurement;
    @Getter @Setter protected Map<String, String> tags = new HashMap<>();
    @Getter @Setter protected Map<String, String> fields = new HashMap<>();
    @Getter @Setter protected long time;

}
