package org.cobbzilla.wizard.model.entityconfig;

import org.cobbzilla.wizard.model.Identifiable;

import java.util.Map;

public interface VerifyLogAware<T> {

    T beforeDiff (T thing, Map<String, Identifiable> context, Object resolver) throws Exception;

    default String uuidOf(Map<String, Identifiable> ctx, Class<? extends Identifiable> clazz) {
        return ctxVar(ctx, clazz).getUuid();
    }

    default <T extends Identifiable> T ctxVar(Map<String, Identifiable> ctx, Class<T> clazz) {
        return (T) ctx.get(clazz.getSimpleName());
    }
}
