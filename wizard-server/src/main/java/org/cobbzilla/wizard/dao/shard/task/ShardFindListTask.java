package org.cobbzilla.wizard.dao.shard.task;

import org.cobbzilla.wizard.dao.shard.SimpleShardTask;
import org.cobbzilla.wizard.dao.shard.SingleShardDAO;
import org.cobbzilla.wizard.model.shard.Shardable;
import org.cobbzilla.wizard.util.ResultCollectorBase;

import java.util.List;
import java.util.Set;

public abstract class ShardFindListTask<E extends Shardable, D extends SingleShardDAO<E>> extends SimpleShardTask<E, D, List<E>> {

    public ShardFindListTask(D dao, Set<ShardTask<E, D, List<E>>> tasks) {
        super(dao, tasks, new ResultCollectorBase());
    }

    @Override protected List<E> execTask() { return find(); }

    protected abstract List<E> find();

}
