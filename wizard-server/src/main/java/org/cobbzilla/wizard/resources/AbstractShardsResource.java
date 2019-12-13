package org.cobbzilla.wizard.resources;

import org.cobbzilla.wizard.dao.shard.ShardMapDAO;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.shard.ShardMap;
import org.cobbzilla.wizard.model.shard.ShardSetStatus;
import org.cobbzilla.wizard.server.config.HasDatabaseConfiguration;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

public abstract class AbstractShardsResource<E extends ShardMap, A extends Identifiable> {

    private static final String[] UPDATE_FIELDS = {"range", "url", "allowRead", "allowWrite"};

    protected abstract boolean isAuthorized(ContainerRequest ctx, A account);
    protected abstract ShardMapDAO<E> getShardDAO();

    @Autowired private HasDatabaseConfiguration configuration;

    class ShardContext {
        public A account;
        public E shard;
        public ShardContext (ContainerRequest ctx) { this(ctx, null, null); }
        public ShardContext (ContainerRequest ctx, String shardSet) { this(ctx, shardSet, null); }
        public ShardContext (ContainerRequest ctx, String shardSet, String shardUuid) {
            account = userPrincipal(ctx);
            if (!isAuthorized(ctx, account)) throw forbiddenEx();
            if (shardSet != null) {
                if (!getConfiguredShardSets().contains(shardSet)) throw notFoundEx(shardSet);
            }
            if (shardUuid != null) {
                shard = getShardDAO().findByUuid(shardUuid);
                if (shard == null) throw notFoundEx(shardUuid);
            }
        }
    }

    private List<ShardSetStatus> toShardSetStatus(Collection<String> names) {
        final List<ShardSetStatus> list = new ArrayList<>();
        for (String name : names) {
            list.add(getShardDAO().validate(name));
        }
        return list;
    }

    protected Collection<String> getConfiguredShardSets() { return configuration.getDatabase().getShardSetNames(); }

    @GET
    public Response findAllShardSets (@Context ContainerRequest context) {
        final ShardContext ctx = new ShardContext(context);
        return ok(toShardSetStatus(getConfiguredShardSets()));
    }

    @GET
    @Path("/{shardSet}")
    public Response findShardSet(@Context ContainerRequest context,
                                 @PathParam("shardSet") String shardSet) {
        final ShardContext ctx = new ShardContext(context, shardSet);
        return ok(getShardDAO().validate(shardSet));
    }

    @GET
    @Path("/{shardSet}/shard/{uuid}")
    public Response findShard(@Context ContainerRequest context,
                              @PathParam("uuid") String uuid,
                              @PathParam("shardSet") String shardSet) {
        final ShardContext ctx = new ShardContext(context, shardSet, uuid);
        return ok(ctx.shard);
    }

    @PUT
    @Path("/{shardSet}")
    public Response createShard (@Context ContainerRequest context,
                                 @PathParam("shardSet") String shardSet,
                                 @Valid ShardMap request) {
        final ShardContext ctx = new ShardContext(context, shardSet);
        if (!request.getShardSet().equals(shardSet)) return invalid("err.shardSet.mismatch");
        final E shard = getShardDAO().newEntity();
        copy(shard, request, UPDATE_FIELDS);
        shard.setShardSet(shardSet);
        return ok(getShardDAO().create(shard));
    }

    @POST
    @Path("/{shardSet}")
    public Response updateShardSet (@Context ContainerRequest context,
                                    @PathParam("shardSet") String shardSet,
                                    @Valid E[] map) {

        final ShardContext ctx = new ShardContext(context, shardSet);
        if (!getConfiguredShardSets().contains(shardSet)) return notFound(shardSet);
        if (!getShardDAO().validate(shardSet, map).isValid()) return invalid("err.shardSet.invalid");

        // disable all current shards
        for (E shard : getShardDAO().findByShardSet(shardSet)) {
            shard.setAllowRead(false);
            shard.setAllowWrite(false);
            getShardDAO().update(shard);
        }

        // set new shard set
        for (E shard : map) {
            getShardDAO().create(shard);
        }

        return ok(getShardDAO().findByShardSet(shardSet));
    }

    @POST
    @Path("/{shardSet}/shard/{uuid}")
    public Response updateShard (@Context ContainerRequest context,
                                 @PathParam("shardSet") String shardSet,
                                 @PathParam("uuid") String uuid,
                                 @Valid ShardMap request) {
        final ShardContext ctx = new ShardContext(context, shardSet, uuid);
        if (!request.getShardSet().equals(shardSet)) return invalid("err.shardSet.mismatch");
        copy(ctx.shard, request, UPDATE_FIELDS);
        return ok(getShardDAO().update(ctx.shard));
    }

    @DELETE
    @Path("/{shardSet}/shard/{uuid}")
    public Response deleteShard (@Context ContainerRequest context,
                                 @PathParam("shardSet") String shardSet,
                                 @PathParam("uuid") String uuid) {
        final ShardContext ctx = new ShardContext(context, shardSet, uuid);

        getShardDAO().refreshCache(true);

        if (!getShardDAO().validateWithShardRemoved(shardSet, ctx.shard).isValid()) {
            return invalid("err.deleteWouldCreateInvalidShardSet");
        }

        getShardDAO().delete(uuid);
        return ok();
    }
}