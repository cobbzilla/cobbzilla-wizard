package org.cobbzilla.wizard.dao;

import org.cobbzilla.wizard.model.AuditLog;

import javax.validation.Valid;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public abstract class AuditLogDAO<E extends AuditLog> extends AbstractCRUDDAO<E> {

    public abstract String getEncryptionKey ();

    @Override public Object preCreate(@Valid E entity) {
        entity = prepare(entity);
        return super.preCreate(entity);
    }

    @Override public Object preUpdate(@Valid E entity) {
        entity = prepare(entity);
        return super.preUpdate(entity);
    }

    protected E prepare(@Valid E entity) {
        final String key = getEncryptionKey();
        if (empty(key)) {
            entity.setKeyHash("-not-encrypted-");
            entity.setRecordHash("-not-encrypted-");
        } else {
            entity = entity.encrypt(key);
        }
        return entity;
    }

}
