package org.cobbzilla.wizard.dao.shard.task;

import lombok.AllArgsConstructor;
import org.cobbzilla.wizard.dao.shard.SingleShardDAO;
import org.cobbzilla.wizard.model.shard.Shardable;

import java.util.List;
import java.util.Set;

public class ShardFindByFieldEqualAndFieldLikeTask<E extends Shardable, D extends SingleShardDAO<E>> extends ShardFindListTask<E, D> {

    @Override protected List<E> find() { return dao.findByFieldEqualAndFieldLike(eqField, eqValue, likeField, likeValue); }

    @AllArgsConstructor
    public static class Factory<E extends Shardable, D extends SingleShardDAO<E>>
            extends ShardTaskFactoryBase<E, D, List<E>> {
        private String eqField;
        private Object eqValue;
        private String likeField;
        private String likeValue;

        @Override public ShardFindByFieldEqualAndFieldLikeTask<E, D> newTask(D dao) {
            return new ShardFindByFieldEqualAndFieldLikeTask(dao, tasks, eqField, eqValue, likeField, likeValue);
        }
    }

    private String eqField;
    private Object eqValue;
    private String likeField;
    private String likeValue;

    public ShardFindByFieldEqualAndFieldLikeTask(D dao, Set<ShardTask<E, D, List<E>>> tasks, String eqField, Object eqValue, String likeField, String likeValue) {
        super(dao, tasks);
        this.eqField = eqField;
        this.eqValue = eqValue;
        this.likeField = likeField;
        this.likeValue = likeValue;
    }

}
