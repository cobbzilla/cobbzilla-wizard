package org.cobbzilla.wizard.client.script;

import com.github.jknack.handlebars.Handlebars;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.CloseableHttpClient;
import org.cobbzilla.util.collection.multi.MultiResultDriverBase;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.reflect.ObjectFactory;
import org.cobbzilla.util.reflect.ReflectionUtil;

import java.util.Map;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true) @Slf4j
public class ApiMultiScriptDriver extends MultiResultDriverBase {

    @Getter @Setter private ApiRunner apiRunner;
    @Getter @Setter private Handlebars handlebars;
    @Getter @Setter private String testTemplate;
    @Getter @Setter private ObjectFactory<CloseableHttpClient> httpClientFactory;

    @Getter private Map<String, Object> context;
    @Override public void setContext(Map<String, Object> context) { this.context = context; }

    @Getter @Setter private int maxConcurrent;
    @Getter @Setter private long timeout;

    @Override public void before() { apiRunner.reset(); }

    protected String getTestName(Object task) { return ReflectionUtil.get(task, "name", ""+task.hashCode()); }

    @Override protected String successMessage(Object task) { return getTestName(task); }
    @Override protected String failureMessage(Object task) { return getTestName(task); }

    protected boolean resetApiContext() { return true; }

    @Override protected void run(Object task) throws Exception {
        @Cleanup final CloseableHttpClient httpClient = httpClientFactory.create();
        final ApiRunner api = new ApiRunner(apiRunner, httpClient);
        if (resetApiContext()) api.getContext().clear();
        api.run(taskToScript(task));
    }

    protected String taskToScript(Object task) {
        final Map<String, Object> ctx = ReflectionUtil.toMap(task);
        return HandlebarsUtil.apply(handlebars, testTemplate, ctx);
    }

}
