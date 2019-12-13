package org.cobbzilla.wizard.dao.shard.task;

import lombok.AllArgsConstructor;
import org.cobbzilla.wizard.dao.shard.SingleShardDAO;
import org.cobbzilla.wizard.model.shard.Shardable;

import java.util.List;
import java.util.Set;

public class ShardFindBy2FieldsTask<E extends Shardable, D extends SingleShardDAO<E>> extends ShardFindListTask<E, D> {

    @Override protected List<E> find() { return dao.findByFields(f1, v1, f2, v2); }

    @AllArgsConstructor
    public static class Factory<E extends Shardable, D extends SingleShardDAO<E>>
            extends ShardTaskFactoryBase<E, D, List<E>> {
        private String f1;
        private Object v1;
        private String f2;
        private Object v2;

        @Override public ShardFindBy2FieldsTask<E, D> newTask(D dao) {
            return new ShardFindBy2FieldsTask(dao, tasks, f1, v1, f2, v2);
        }
    }

    private String f1;
    private Object v1;
    private String f2;
    private Object v2;

    public ShardFindBy2FieldsTask(D dao, Set<ShardTask<E, D, List<E>>> tasks, String f1, Object v1, String f2, Object v2) {
        super(dao, tasks);
        this.f1 = f1;
        this.v1 = v1;
        this.f2 = f2;
        this.v2 = v2;
    }

}
