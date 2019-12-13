package org.cobbzilla.wizard.resources;

import org.cobbzilla.wizard.dao.NamedIdentityBaseDAO;
import org.cobbzilla.wizard.model.NamedIdentityBase;
import org.glassfish.jersey.server.ContainerRequest;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@SuppressWarnings("Duplicates")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public abstract class NamedResource<E extends NamedIdentityBase> {

    public abstract NamedIdentityBaseDAO<E> getDao ();

    protected boolean canCreate(ContainerRequest ctx) { return true; }
    protected boolean canUpdate(ContainerRequest ctx) { return true; }
    protected boolean canDelete(ContainerRequest ctx) { return true; }

    @GET
    public Response findAll (@Context ContainerRequest ctx) {
        return ok(getDao().findAll());
    }

    @GET @Path("/{name}")
    public Response findOne (@Context ContainerRequest ctx,
                             @PathParam("name") String name) {
        final E found = getDao().findByName(name);
        return found != null ? ok(found) : notFound(name);
    }

    @PUT
    public Response create (@Context ContainerRequest ctx,
                            @Valid E thing) {
        if (!canCreate(ctx)) return forbidden();
        final E found = getDao().findByName(thing.getName());
        if (found != null) return invalid("err.name.notUnique");
        return ok(getDao().create(getDao().newEntity(thing)));
    }

    @POST @Path("/{name}")
    public Response update (@Context ContainerRequest ctx,
                            @PathParam("name") String name,
                            @Valid E thing) {
        if (!canUpdate(ctx)) return forbidden();
        if (!name.equals(thing.getName())) return invalid("err.name.mismatch");
        final E found = getDao().findByName(name);
        if (found == null) return notFound(name);
        return ok(getDao().update((E) found.update(thing)));
    }

    @DELETE @Path("/{name}")
    public Response delete (@Context ContainerRequest ctx,
                            @PathParam("name") String name) {
        if (!canDelete(ctx)) return forbidden();
        final E found = getDao().findByName(name);
        if (found == null) return notFound(name);
        getDao().delete(name);
        return ok();
    }

}
