package org.cobbzilla.wizard.server.config;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.wizard.model.shard.ShardMap;
import org.cobbzilla.wizard.model.shard.Shardable;

import java.util.HashSet;
import java.util.Set;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class DatabaseShardConfiguration extends DatabaseConfiguration {

    @Getter @Setter private volatile ShardSetConfiguration[] shard;

    public ShardSetConfiguration getShard (String shardSet) {
        if (shard != null) {
            for (ShardSetConfiguration c : shard) {
                if (c.getName().equals(shardSet)) return c;
            }
        }
        return null;
    }

    public DatabaseConfiguration getShardDatabaseConfiguration(ShardMap map) {
        final DatabaseConfiguration config = new DatabaseConfiguration();
        config.setDriver(driver);
        config.setUrl(map.getUrl());
        config.setUser(user);
        config.setPassword(password);
        config.setEncryptionEnabled(encryptionEnabled);
        config.setEncryptionKey(encryptionKey);
        config.setEncryptorPoolSize(encryptorPoolSize);
        config.setHibernate(new HibernateConfiguration(hibernate));
        config.getHibernate().setValidationMode("validate");
        return config;
    }

    @Getter(lazy=true) private final Set<String> shardSetNames = initShardSetNames();

    protected Set<String> initShardSetNames() {
        final Set<String> names = new HashSet<>();
        if (shard != null) for (ShardSetConfiguration config : shard) names.add(config.getName());
        return names;
    }

    @Override public boolean hasShards() { return !getShardSetNames().isEmpty(); }

    public int getLogicalShardCount(String shardSet) {
        for (ShardSetConfiguration config : shard) if (config.getName().equals(shardSet)) return config.getLogicalShards();
        return ShardSetConfiguration.DEFAULT_LOGICAL_SHARDS;
    }

    public <E extends Shardable> String getShardSetName(Class<E> entityClass) {
        if (empty(shard)) return null;
        for (ShardSetConfiguration config : shard) {
            if (config.getEntity().equals(entityClass.getName())) return config.getName();
        }
        return null;
    }
}
