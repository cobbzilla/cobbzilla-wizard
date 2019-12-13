package org.cobbzilla.wizard.dao;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.ExpirableBase;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.*;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;

@Slf4j
public abstract class BackgroundFetcherDAO<E extends ExpirableBase> extends AbstractRedisDAO<E> {

    @Getter @Setter private long recalculateInterval = TimeUnit.MINUTES.toMillis(2);

    @Override public E get(Serializable id) { return get(id, null); }

    public E get(Serializable id, Map<String, Object> context) {
        final E entity = super.get(id);
        if (entity != null) {
            if (!entity.hasUuid()) {
                // should never happen
                log.warn("get("+id+"): job was missing UUID, re-adding (should never happen)");
                entity.setUuid(id.toString());
            }
            // if this result is really old, queue another job
            final String ctimeString = super.getMetadata(metadataCtimeKey(entity));
            if (!empty(ctimeString)) {
                try {
                    final long ctime = Long.parseLong(ctimeString);
                    if (now() - ctime > getRecalculateInterval()) queueJob(id.toString(), context);
                } catch (Exception e) {
                    log.warn("get("+id+"): error checking job status: "+e);
                }
            }
            return entity;
        } else {
            return queueJob(id.toString(), context);
        }
    }

    public String metadataCtimeKey(E entity) { return entity.getUuid()+".ctime"; }

    private Map<String, EntityJobResult> jobs = new ConcurrentHashMap<>();

    @Getter(lazy=true) private final ExecutorService executor = initExecutor();
    private ExecutorService initExecutor() { return Executors.newFixedThreadPool(getThreadPoolSize()); }

    public int getThreadPoolSize () { return 1; }
    protected abstract Callable<E> newEntityJob(String uuid, Map<String, Object> context);

    public E queueJob(String uuid, Map<String, Object> context) {
        EntityJobResult result = jobs.get(uuid);
        try {
            if (result != null) {
                if (result.isRunning()) {
                    // todo -- if it has been running too long, kill it and maybe restart it
                    return null;
                }
                if (result.getSummaryAge() > getRecalculateInterval()) {
                    jobs.put(uuid, new EntityJobResult(getExecutor().submit(newEntityJob(uuid, context))));
                }
                return result.getEntity();

            } else {
                jobs.put(uuid, new EntityJobResult(getExecutor().submit(newEntityJob(uuid, context))));
                return null;
            }
        } catch (Exception e) {
            log.error("queueJob: "+e, e);
            return null;
        }
    }

    public boolean isRunning(String uuid) {
        final EntityJobResult result = jobs.get(uuid);
        return result == null ? null : result.isRunning();
    }

    private class EntityJobResult {

        private Future<E> future;

        public EntityJobResult(Future<E> future) { this.future = future; }

        private E entity = null;
        private Long lastRun = null;

        public boolean isRunning() { return getEntity() == null; }

        public long getSummaryAge () { return lastRun == null ? Long.MAX_VALUE : now() - lastRun; }

        public E getEntity() {
            if (entity != null) return entity;
            try {
                entity = future.get(50, TimeUnit.MILLISECONDS);
                lastRun = now();
                update(entity);
                // record in redis when this was set, so if it gets too old we will kick off a job
                setMetadata(metadataCtimeKey(entity), String.valueOf(now()));
                jobs.remove(entity.getUuid());
                return entity;

            } catch (InterruptedException|ExecutionException e) {
                return die(e);

            } catch (TimeoutException e) {
                return null;
            }
        }
    }

}
