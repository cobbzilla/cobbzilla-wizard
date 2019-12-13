package org.cobbzilla.wizard.resources;

import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.wizard.model.NamedIdentityBase;
import org.glassfish.jersey.server.ContainerRequest;

import static org.cobbzilla.wizard.resources.ResourceUtil.optionalUserPrincipal;

public abstract class NamedSystemResource<E extends NamedIdentityBase> extends NamedResource<E> {

    protected boolean isAdmin(ContainerRequest ctx) {
        final Object thing = optionalUserPrincipal(ctx);
        if (thing == null) return false;
        final Object admin = ReflectionUtil.get(thing, "admin");
        return admin != null && Boolean.valueOf(admin.toString());
    }

    @Override protected boolean canCreate(ContainerRequest ctx) { return isAdmin(ctx); }
    @Override protected boolean canUpdate(ContainerRequest ctx) { return isAdmin(ctx); }
    @Override protected boolean canDelete(ContainerRequest ctx) { return isAdmin(ctx); }

}
