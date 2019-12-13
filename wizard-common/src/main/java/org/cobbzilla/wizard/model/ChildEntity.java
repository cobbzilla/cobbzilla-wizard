package org.cobbzilla.wizard.model;

import org.apache.commons.beanutils.BeanUtils;

import javax.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class ChildEntity<C, P> extends IdentifiableBase {

    public abstract P getParent ();
    public abstract void setParent (P parent);

    public void update(C child) {
        String existingUuid = getUuid();
        P existingParent = getParent();
        try {
            BeanUtils.copyProperties(this, child);

        } catch (Exception e) {
            throw new IllegalArgumentException("update: error copying properties: "+e, e);

        } finally {
            // Do not allow these to be updated
            setUuid(existingUuid);
            setParent(existingParent);
        }
    }

}
