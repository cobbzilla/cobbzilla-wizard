package org.cobbzilla.wizard.server.config;

import lombok.Setter;
import org.cobbzilla.util.collection.SingletonList;

import java.util.Iterator;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class ElasticSearchConfig {

    public static final String DEFAULT_ES_URL = "http://127.0.0.1:9300";
    public static final SingletonList<String> DEFAULT_ES_LIST = new SingletonList<>(DEFAULT_ES_URL);
    public static final String DEFAULT_CLUSTER = "elasticsearch";

    @Setter private String cluster = DEFAULT_CLUSTER;
    public String getCluster() { return empty(cluster) ? DEFAULT_CLUSTER : cluster; }

    @Setter private List<String> servers;

    public synchronized List<String> getServers () {
        if (empty(servers)) return DEFAULT_ES_LIST;
        for (Iterator<String> iter = servers.iterator(); iter.hasNext(); ) {
            // it's ok if a name is empty, we just skip it
            final String server = iter.next();
            if (empty(server)) iter.remove();
        }
        return empty(servers) ? DEFAULT_ES_LIST : servers;
    }

}
