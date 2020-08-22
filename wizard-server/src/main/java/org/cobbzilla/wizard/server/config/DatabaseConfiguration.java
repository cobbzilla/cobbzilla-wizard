package org.cobbzilla.wizard.server.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.cobbzilla.wizard.model.shard.ShardMap;
import org.cobbzilla.wizard.model.shard.Shardable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@NoArgsConstructor
public class DatabaseConfiguration {

    public DatabaseConfiguration(DatabaseConfiguration other) { copy(this, other); }

    @Getter @Setter private String driver;
    @Getter @Setter private String url;
    @Getter @Setter private String user;
    @JsonIgnore @Getter @Setter private String password;

    @Getter @Setter private DatabaseConnectionPoolConfiguration pool = new DatabaseConnectionPoolConfiguration();

    @Getter @Setter private boolean encryptionEnabled = false;
    @JsonIgnore @Getter @Setter private String encryptionKey;
    @Getter @Setter private int encryptorPoolSize = 5;

    @Getter @Setter private HibernateConfiguration hibernate;

    @Getter @Setter private boolean migrationEnabled = true;

    private final List<Runnable> postDataSourceSetupHandlers = new ArrayList<>();
    public void addPostDataSourceSetupHandler (Runnable handler) { postDataSourceSetupHandlers.add(handler); }
    public void runPostDataSourceSetupHandlers () {
        for (Runnable r : postDataSourceSetupHandlers) r.run();
    }

    @JsonIgnore public String getDatabaseName() { return getUrl().substring(getUrl().lastIndexOf('/')+1); }

    @JsonIgnore public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

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

    public boolean hasShards() { return !getShardSetNames().isEmpty(); }

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

    public void setDatabaseName(String dbName) {
        final String url = getUrl();
        final int lastSlash = url.lastIndexOf('/');
        if (lastSlash == -1) die("setDatabaseName: invalid URL: "+url);
        final int qPos = url.indexOf('?', lastSlash);
        setUrl(url.substring(0, lastSlash+1) + dbName + (qPos == -1 ? "" : url.substring(qPos)));
    }
}
