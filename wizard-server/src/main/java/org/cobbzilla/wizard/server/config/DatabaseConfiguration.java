package org.cobbzilla.wizard.server.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.cobbzilla.util.collection.SingletonSet;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@NoArgsConstructor
public class DatabaseConfiguration {

    public DatabaseConfiguration(DatabaseConfiguration other) { copy(this, other); }

    @Getter @Setter protected String driver;
    @Getter @Setter protected String url;
    @Getter @Setter protected String user;
    @JsonIgnore @Getter @Setter protected String password;

    @Getter @Setter protected DatabaseConnectionPoolConfiguration pool = new DatabaseConnectionPoolConfiguration();

    @Getter @Setter protected boolean encryptionEnabled = false;
    @JsonIgnore @Getter @Setter protected String encryptionKey;
    @Getter @Setter protected int encryptorPoolSize = 5;

    @Getter @Setter protected HibernateConfiguration hibernate;

    @Getter @Setter protected boolean migrationEnabled = true;
    @Getter @Setter protected boolean migrationBaselineOnly = false;

    protected final List<Runnable> postDataSourceSetupHandlers = new ArrayList<>();
    public void addPostDataSourceSetupHandler (Runnable handler) { postDataSourceSetupHandlers.add(handler); }
    public void runPostDataSourceSetupHandlers () {
        for (Runnable r : postDataSourceSetupHandlers) r.run();
    }

    @JsonIgnore public String getDatabaseName() { return getUrl().substring(getUrl().lastIndexOf('/')+1); }

    @JsonIgnore public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    // Use DatabaseShardConfiguration if you want sharding
    public boolean hasShards() { return false; }
    @Getter(lazy=true) private final Set<String> shardSetNames = initShardSetNames();
    public static final String DEFAULT_SHARD = "getShardSetName:default";
    protected Set<String> initShardSetNames() { return new SingletonSet<>(DEFAULT_SHARD); }

    public void setDatabaseName(String dbName) {
        final String url = getUrl();
        final int lastSlash = url.lastIndexOf('/');
        if (lastSlash == -1) die("setDatabaseName: invalid URL: "+url);
        final int qPos = url.indexOf('?', lastSlash);
        setUrl(url.substring(0, lastSlash+1) + dbName + (qPos == -1 ? "" : url.substring(qPos)));
    }
}
