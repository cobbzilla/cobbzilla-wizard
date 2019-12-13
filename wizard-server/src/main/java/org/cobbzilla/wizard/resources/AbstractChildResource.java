package org.cobbzilla.wizard.resources;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.dao.AbstractChildCRUDDAO;
import org.cobbzilla.wizard.model.ChildEntity;
import org.cobbzilla.wizard.model.Identifiable;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;

import static org.cobbzilla.wizard.resources.AbstractResource.UUID;
import static org.cobbzilla.wizard.resources.AbstractResource.UUID_PARAM;
import static org.cobbzilla.wizard.resources.ResourceUtil.ok;
import static org.cobbzilla.wizard.resources.ResourceUtil.status;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public abstract class AbstractChildResource<C extends ChildEntity<C, P>, P extends Identifiable> {

    public static final String PARENT_UUID_PARAM = "parentUuid";
    public static final String PARENT_UUID = "{"+PARENT_UUID_PARAM+"}";

    protected abstract AbstractChildCRUDDAO<C, P> dao ();
    protected String parentResourcePath;
    protected String endPoint;

    @Path("/"+UUID)
    @GET
    public Response find(@PathParam(UUID_PARAM) String childUuid) {
        final C child = dao().findByUuid(childUuid);
        return child == null ? ResourceUtil.notFound(childUuid) : Response.ok(child).build();
    }

    @GET
    public List<C> getByParent(@PathParam(PARENT_UUID_PARAM) String parentUuid) {
        return filterChildren(dao().findByParentUuid(parentUuid));
    }

    protected List<C> filterChildren(List<C> list) { return list; }

    @POST
    public Response create(@PathParam(PARENT_UUID_PARAM) String parentUuid, @Valid C child) {
        child = dao().create(parentUuid, child);
        return Response.created(URI.create(parentResourcePath + "/" + parentUuid + endPoint + "/" + child.getUuid())).build();
    }

    @Path("/"+UUID)
    @PUT
    public Response update(@PathParam(UUID_PARAM) String uuid, @Valid C child) {
        try {
            C found = dao().findByUuid(uuid);
            if (found == null) {
                return ResourceUtil.notFound(uuid);
            }
            found.update(child);
            dao().update(found);
            return Response.ok(found).build();
        } catch (Exception e) {
            log.error("Status were not updated:", e);
            return status(Response.Status.PRECONDITION_FAILED);
        }
    }

    @Path("/"+UUID)
    @DELETE
    public Response delete(@PathParam(UUID_PARAM) String uuid) {
        dao().delete(uuid);
        return ok();
    }
}
