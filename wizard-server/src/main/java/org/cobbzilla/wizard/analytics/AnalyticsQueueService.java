package org.cobbzilla.wizard.analytics;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.http.HttpUtil;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.cobbzilla.util.daemon.DaemonThreadFactory.fixedPool;
import static org.cobbzilla.util.daemon.ZillaRuntime.daemon;
import static org.cobbzilla.util.http.HttpMethods.POST;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public class AnalyticsQueueService {

    @Autowired private RestServerConfiguration configuration;

    private final Map<String, AnalyticsData> analyticsData = new HashMap<>();
    private final ExecutorService exec = fixedPool(10, "AnalyticsQueueService.exec");

    public void report(String uuid, AnalyticsData data) {
        synchronized (analyticsData) { analyticsData.put(uuid, data); }
    }

    @PostConstruct public void startSendingAnalytics() {

        final AnalyticsConfiguration analyticsConfig = configuration.getAnalytics();
        if (analyticsConfig == null || !analyticsConfig.valid()) {
            log.info("startSendingAnalytics: config was null or invalid, not sending analytics");
            return;
        }

        final AnalyticsHandler handler = configuration.getAnalyticsHandler();
        if (handler == null) {
            log.info("startSendingAnalytics: configuration.getAnalyticsHandler returned null, not sending analytics");
            return;
        }

        final String writeUrl = handler.getWriteUrl();
        final int httpSuccess = handler.getHttpSuccess();
        final String simpleClass = getClass().getSimpleName();
        final long reportInterval = analyticsConfig.getReportInterval();

        daemon(() -> {
            while (true) {
                sleep(reportInterval);

                final Map<String, AnalyticsData> copy;
                synchronized (analyticsData) {
                    if (analyticsData.isEmpty()) continue;
                    copy = new HashMap<>();
                    copy.putAll(analyticsData);
                    analyticsData.clear();
                }

                copy.forEach((key, value) -> exec.submit(() -> {
                    final HttpRequestBean request = new HttpRequestBean(POST, writeUrl, value.buildMessage());
                    try {
                        final HttpResponseBean response = HttpUtil.getResponse(request);
                        if (response.getStatus() != httpSuccess) {
                            log.error(simpleClass+": error writing analytics:" + writeUrl + ": expected HTTP status " + httpSuccess + ", response=" + response);
                        }
                    } catch (Exception e) {
                        log.error(simpleClass+": error writing analytics (" + writeUrl + "): " + e, e);
                    }
                }));
            }
        });
    }

}
