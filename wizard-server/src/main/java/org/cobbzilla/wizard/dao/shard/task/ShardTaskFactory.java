package org.cobbzilla.wizard.dao.shard.task;

import org.cobbzilla.wizard.dao.shard.SingleShardDAO;
import org.cobbzilla.wizard.model.shard.Shardable;

import java.util.Set;

public interface ShardTaskFactory<E extends Shardable, D extends SingleShardDAO<E>, R> {

    ShardTask<E, D, R> newTask (D dao);

    Set<ShardTask<E, D, R>> getTasks ();

    void cancelTasks();
}
