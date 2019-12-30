package org.cobbzilla.wizard.resources;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.wizard.dao.DAO;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.search.SearchQuery;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

import static org.cobbzilla.util.collection.ArrayUtil.singletonArray;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public abstract class AbstractResource<T extends Identifiable> {

    public static final String UUID_PARAM = "uuid";
    public static final String UUID = "{"+UUID_PARAM+"}";

//    protected abstract AbstractCRUDDAO<T> dao ();
    protected abstract DAO<T> dao ();

    protected abstract String getEndpoint();

    @GET
    public Response index(@QueryParam(SearchQuery.PARAM_USE_PAGINATION) Boolean usePagination,
                          @QueryParam(SearchQuery.PARAM_PAGE_NUMBER) Integer pageNumber,
                          @QueryParam(SearchQuery.PARAM_PAGE_SIZE) Integer pageSize,
                          @QueryParam(SearchQuery.PARAM_SORT_FIELD) String sortField,
                          @QueryParam(SearchQuery.PARAM_SORT_ORDER) String sortOrder,
                          @QueryParam(SearchQuery.PARAM_FILTER) String filter,
                          @QueryParam(SearchQuery.PARAM_BOUNDS) String bounds) {

        if (usePagination == null || !usePagination) return findAll();
        return Response.ok(dao().search(new SearchQuery(pageNumber, pageSize, sortField, sortOrder, filter, parseBounds(bounds)))).build();
    }

    public static NameAndValue[] parseBounds(String bounds) {
        try {
            return empty(bounds) ? NameAndValue.EMPTY_ARRAY : json(bounds, NameAndValue[].class);
        } catch (Exception e) {
            try {
                return singletonArray(json(bounds, NameAndValue.class));
            } catch (Exception e2) {
                return die("parseBounds: invalid bounds (" + bounds + "): " + e2);
            }
        }
    }

    protected Response findAll() { return Response.ok(dao().findAll()).build(); }

    @POST
    public Response create(@Valid T thing) {

        final Object context;
        context = preCreate(thing);
        thing = dao().create(thing);
        thing = postCreate(thing, context);

        final URI location = URI.create(thing.getUuid());
        return Response.created(location).build();
    }

    protected Object preCreate(T thing) { return null; }
    protected T postCreate(T thing, Object context) { return thing; }

    @Path("/"+UUID)
    @GET
    public Response find(@PathParam(UUID_PARAM) String uuid) {
        final T thing = dao().findByUuid(uuid);
        return thing == null ? ResourceUtil.notFound(uuid) : Response.ok(postProcess(thing)).build();
    }

    protected T postProcess(T thing) { return thing; }

    @Path("/"+UUID)
    @PUT
    public Response update(@PathParam(UUID_PARAM) String uuid, @Valid T thing) {
        Response response;
        final DAO<T> dao = dao();
        final T found = dao.findByUuid(uuid);
        if (found != null) {
            thing.setUuid(uuid);
            final Object context = preUpdate(thing);
            dao.update(thing);
            thing = postUpdate(thing, context);
            response = Response.noContent().build();
        } else {
            response = ResourceUtil.notFound(uuid);
        }
        return response;
    }

    protected Object preUpdate(T thing) { return null; }
    protected T postUpdate(T thing, Object context) { return thing; }

    @Path("/"+UUID)
    @DELETE
    public Response delete(@PathParam(UUID_PARAM) String uuid) {
        final DAO<T> dao = dao();
        final T found = dao.findByUuid(uuid);
        if (found == null) return ResourceUtil.notFound();
        final Object context = preDelete(found);
        dao.delete(uuid);
        postDelete(found, context);
        return Response.noContent().build();
    }

    protected Object preDelete(T thing) { return null; }
    protected void postDelete(T thing, Object context) {}

}
