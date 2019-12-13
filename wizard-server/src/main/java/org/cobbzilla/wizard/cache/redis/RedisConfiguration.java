package org.cobbzilla.wizard.cache.redis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor @AllArgsConstructor
public class RedisConfiguration {

    @Getter @Setter private String host = "127.0.0.1";
    @Getter @Setter private int port = 6379;
    @Getter @Setter private String key;
    @Setter private String prefix;
    public String getPrefix () { return prefix == null ? "" : prefix; }

    public RedisConfiguration (String key) { this.key = key; }

}
