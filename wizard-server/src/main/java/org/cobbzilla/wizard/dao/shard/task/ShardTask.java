package org.cobbzilla.wizard.dao.shard.task;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.dao.shard.SingleShardDAO;
import org.cobbzilla.wizard.model.shard.Shardable;
import org.cobbzilla.wizard.util.ResultCollector;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@AllArgsConstructor @Slf4j
public abstract class ShardTask<E extends Shardable, D extends SingleShardDAO<E>, R> implements Callable, Comparable {

    protected D dao;
    protected Set<ShardTask<E, D, R>> tasks;
    protected ResultCollector resultCollector;
    @Getter @Setter private boolean customCollector = false;

    protected final AtomicBoolean cancelled = new AtomicBoolean(false);

    protected void cancelTasks() {
        if (tasks != null) for (ShardTask task : tasks) task.cancel();
    }

    public void cancel() { cancelled.set(true); }

    public boolean canBegin() {
        if (cancelled.get()) return false;
        if (tasks != null) tasks.add(this);
        return true;
    }

    @Override public Object call() throws Exception {
        Object rval = null;
        try {
            if (!canBegin()) {
                return die("call: canBegin returned false");
            } else {
                rval = execTask();
                if (!customCollector && resultCollector != null) resultCollector.addResult(rval);
            }
        } catch (Exception e) {
            log.error("call: "+e, e);
            throw e;
        } finally {
            log.info("call: in finally, rval="+rval);
        }
        return rval;
    }

    protected abstract Object execTask();

    // for some reason Future.get wants this to be Comparable
    @Override public int compareTo(Object o) { return this.hashCode() - o.hashCode(); }

}
