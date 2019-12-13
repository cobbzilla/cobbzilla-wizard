package org.cobbzilla.wizard.dao.shard.task;

import lombok.AllArgsConstructor;
import org.cobbzilla.wizard.dao.shard.SingleShardDAO;
import org.cobbzilla.wizard.model.shard.Shardable;

import java.util.List;
import java.util.Set;

public class ShardFindByFieldInTask<E extends Shardable, D extends SingleShardDAO<E>> extends ShardFindListTask<E, D> {

    @Override protected List<E> find() { return dao.findByFieldIn(field, values); }

    @AllArgsConstructor
    public static class Factory<E extends Shardable, D extends SingleShardDAO<E>>
            extends ShardTaskFactoryBase<E, D, List<E>> {
        private String field;
        private Object[] values;

        @Override public ShardFindByFieldInTask<E, D> newTask(D dao) {
            return new ShardFindByFieldInTask(dao, tasks, field, values);
        }
    }

    private String field;
    private Object[] values;

    public ShardFindByFieldInTask(D dao, Set<ShardTask<E, D, List<E>>> tasks, String field, Object[] values) {
        super(dao, tasks);
        this.field = field;
        this.values = values;
    }

}
