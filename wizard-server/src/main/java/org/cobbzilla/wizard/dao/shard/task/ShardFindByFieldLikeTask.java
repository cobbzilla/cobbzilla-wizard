package org.cobbzilla.wizard.dao.shard.task;

import lombok.AllArgsConstructor;
import org.cobbzilla.wizard.dao.shard.SingleShardDAO;
import org.cobbzilla.wizard.model.shard.Shardable;

import java.util.List;
import java.util.Set;

public class ShardFindByFieldLikeTask<E extends Shardable, D extends SingleShardDAO<E>> extends ShardFindListTask<E, D> {

    @Override protected List<E> find() { return dao.findByFieldLike(field, value); }

    @AllArgsConstructor
    public static class Factory<E extends Shardable, D extends SingleShardDAO<E>>
            extends ShardTaskFactoryBase<E, D, List<E>> {
        private String field;
        private String value;

        @Override public ShardFindByFieldLikeTask<E, D> newTask(D dao) {
            return new ShardFindByFieldLikeTask(dao, tasks, field, value);
        }
    }

    private String field;
    private String   value;

    public ShardFindByFieldLikeTask(D dao, Set<ShardTask<E, D, List<E>>> tasks, String field, String value) {
        super(dao, tasks);
        this.field = field;
        this.value = value;
    }

}
