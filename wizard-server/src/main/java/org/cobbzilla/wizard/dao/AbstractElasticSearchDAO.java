package org.cobbzilla.wizard.dao;

import lombok.AllArgsConstructor;
import lombok.Cleanup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.cobbzilla.util.cache.AutoRefreshingReference;
import org.cobbzilla.util.http.URIUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.server.config.ElasticSearchConfig;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

import java.io.Closeable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.getFirstTypeParam;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

@Slf4j
public abstract class AbstractElasticSearchDAO<E extends Identifiable, Q, R extends Identifiable> {

    public abstract String getIndexName ();
    public abstract String getTypeName ();
    public abstract int getMaxResults ();
    public abstract long getClientRefreshInterval ();

    protected abstract ElasticSearchConfig getConfiguration ();
    protected abstract String getTypeMappingJson();
    protected abstract String getSearchId(E thing);

    @Getter(lazy=true) private final Class<E> entityType = getFirstTypeParam(getClass(), Identifiable.class);

    private final ObjectPool<ESClientReference> clientPool = new GenericObjectPool<>(new BasePooledObjectFactory<ESClientReference>() {
        @Override public ESClientReference create() throws Exception { return new ESClientReference(); }
        @Override public PooledObject<ESClientReference> wrap(ESClientReference esClientReference) { return new DefaultPooledObject<>(esClientReference); }
        @Override public void destroyObject(PooledObject<ESClientReference> p) throws Exception {
            final ESClientReference clientRef = p.getObject();
            if (!clientRef.isEmpty()) clientRef.get().close();
            super.destroyObject(p);
        }
    }, getPoolConfig());

    private GenericObjectPoolConfig getPoolConfig() {
        final GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxTotal(getSearchPoolSize());
        poolConfig.setMinIdle(2);
        poolConfig.setMaxIdle(getSearchPoolSize()/2);
        return poolConfig;
    }

    protected int getSearchPoolSize() { return 10; }

    protected ESClientReference getClient () {
        synchronized (clientPool) {
            try {
                final ESClientReference clientRef = clientPool.borrowObject();
                log.debug("getClient: borrowing client #"+clientRef.hashCode());
                return clientRef;
            } catch (Exception e) {
                return die("getClient: " + e, e);
            }
        }
    }

    protected TransportAddress toTransportAddress(String uri) {
        try {
            return new InetSocketTransportAddress(InetAddress.getByName(URIUtil.getHost(uri)), URIUtil.getPort(uri));
        } catch (UnknownHostException e) {
            return die("toTransportAddress("+uri+"): "+e);
        }
    }

    protected boolean shouldIndex(E entity) { return true; }

    private ExecutorService indexPool = Executors.newFixedThreadPool(getIndexPoolSize());
    protected int getIndexPoolSize() { return 100; }

    public Future<?> index (E entity) {
        if (!shouldIndex(entity)) {
            log.warn("index: refusing to index: "+getSearchId(entity));
            return null;
        }
        return indexPool.submit(new ESIndexJob(entity));
    }

    public boolean delete (String id) {
        try (ESClientReference client = getClient()) {
            return client.get().prepareDelete(getIndexName(), getTypeName(), id).get().isFound();
        }
    }
    public boolean delete (E entity) { return delete(getSearchId(entity)); }

    protected SearchRequestBuilder prepareSearch(Client client) {
        return client.prepareSearch(getIndexName()).setTypes(getTypeName());
    }

    protected boolean isEmptyQuery(Q searchQuery) { return false; }
    protected QueryBuilder getQuery(Q searchQuery) { return matchAllQuery(); }
    protected QueryBuilder getPostFilter(Q searchQuery) { return matchAllQuery(); }
    protected abstract R toSearchResult(E entity);
    protected abstract Comparator<? super R> getComparator(Q searchQuery);

    public SearchResults<R> search(Q searchQuery) {

        // empty query returns nothing
        if (isEmptyQuery(searchQuery)) return new SearchResults<>();

        final SearchResults<R> results = new SearchResults<>();
        final SearchResponse response;

        @Cleanup final ESClientReference client = getClient();
        synchronized (client.get()) {
            final SearchRequestBuilder requestBuilder = prepareSearch(client.get())
                    .setQuery(getQuery(searchQuery))
                    .setPostFilter(getPostFilter(searchQuery))
                    .setFrom(0).setSize(getMaxResults());

            log.info("search: sending to ES:\n"+requestBuilder.toString()+"\n---END JSON\n");
            response = requestBuilder.execute().actionGet();
        }
        final SearchHits hits = response.getHits();

        for (SearchHit hit : hits) {
            final E entity = fromJsonOrDie(hit.getSourceAsString(), getEntityType());
            results.addResult(toSearchResult(entity));
        }

        Collections.sort(results.getResults(), getComparator(searchQuery));
        return results;
    }

    public SearchResponse debugSearch(DebugSearchQuery query) {
//        final List<R> results = new ArrayList<>();
        final SearchResponse response;
        @Cleanup final ESClientReference client = getClient();
        synchronized (client.get()) {
            final SearchRequestBuilder requestBuilder;
            if (query.hasSearchPreparer()) {
                final SearchPreparer preparer = instantiate(query.getSearchPreparer());
                requestBuilder = preparer.prepare(client.get());
            } else {
                requestBuilder = prepareSearch(client.get());
            }
            if (query.hasSource()) {
                requestBuilder.setSource(query.getSource());
            } else {
                requestBuilder
                        .setQuery(query.getQuery())
                        .setPostFilter(query.getFilter())
                        .setFrom(query.getFrom()).setSize(query.getMaxResults());
            }

            log.info("search: sending to ES:\n"+requestBuilder.toString()+"\n---END JSON\n");
            response = requestBuilder.execute().actionGet();
        }
//        for (SearchHit hit : response) {
//
//        }
        return response;
    }

    @AllArgsConstructor
    private class ESIndexJob implements Runnable {
        private final E entity;

        @Override public void run() {
            @Cleanup final ESClientReference client = getClient();
            synchronized (client.get()) {
                try {
                    final String json = toJson(entity);

                    final String searchId = getSearchId(entity);
                    final IndexRequest indexRequest = new IndexRequest(getIndexName(), getTypeName(), searchId).source(json);
                    final UpdateRequest updateRequest = new UpdateRequest(getIndexName(), getTypeName(), searchId)
                            .doc(json)
                            .upsert(indexRequest);
                    final UpdateResponse response = client.get().update(updateRequest).get();

                    if (response.getShardInfo().getSuccessful() == 0 && response.getShardInfo().getFailed() > 0) {
                        log.warn("Error indexing: " + toJsonOrErr(response));
                    }

                } catch (Exception e) {
                    final String msg = "index: " + e;
                    log.error(msg, e);
                    die(msg, e);
                }
            }
        }
    }

    private static final AtomicBoolean checkedIndex = new AtomicBoolean(false);

    private class ESClientReference extends AutoRefreshingReference<Client> implements Closeable {

        @Override public Client refresh() {
            final ElasticSearchConfig config = getConfiguration();
            final Settings settings = Settings.settingsBuilder().put("cluster.name", config.getCluster()).build();

            TransportClient c = TransportClient.builder().settings(settings).build();
            for (String uri : config.getServers()) c = c.addTransportAddress(toTransportAddress(uri));

            if (!checkedIndex.get()) {
                synchronized (checkedIndex) {
                    if (!checkedIndex.get()) {
                        checkedIndex.set(true);
                        try {
                            final IndicesAdminClient indices = c.admin().indices();
                            if (!indices.exists(new IndicesExistsRequest(getIndexName())).actionGet().isExists()) {
                                indices.create(new CreateIndexRequest(getIndexName()).mapping(getTypeName(), getTypeMappingJson())).actionGet();
                            }
                        } catch (Exception e) {
                            die("initClient: error setting up mappings: " + e, e);
                        }
                    }
                }
            }

            return c;
        }

        @Override public long getTimeout() { return getClientRefreshInterval(); }

        @Override public void close() {
            synchronized (clientPool) {
                try {
                    clientPool.returnObject(this);
                    log.debug("close: returning client #"+this.hashCode());
                } catch (Exception e) {
                    die("close: error returning client to pool: " + e, e);
                }
            }
        }
    }

}
