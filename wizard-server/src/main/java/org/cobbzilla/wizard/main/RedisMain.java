package org.cobbzilla.wizard.main;

import org.cobbzilla.util.main.BaseMain;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.cache.redis.RedisConfiguration;
import org.cobbzilla.wizard.cache.redis.RedisService;

public class RedisMain extends BaseMain<RedisOptions> {

    public static void main (String[] args) { main(RedisMain.class, args); }

    @Override protected void run() throws Exception {
        final RedisOptions options = getOptions();
        final RedisConfiguration config = options.getRedisConfiguration();
        final RedisService redisService = new RedisService(config, config.getPrefix(), config.getKey());
        final boolean quiet = options.isQuiet();
        switch (options.getCommand()) {
            case "get":
                for (String key : options.getArguments()) {
                    if (!quiet) out(key+":\n");
                    out(redisService.get(key));
                }
                break;

            case "exists":
                for (String key : options.getArguments()) {
                    out(key+" : "+(redisService.exists(key) ? "exists" : "not found")+"\n");
                }
                break;

            case "list":
                for (String key : options.getArguments()) {
                    if (!quiet) out(key+":\n");
                    out("["+StringUtil.toString(redisService.list(key),", ")+"]");
                }
                break;

            case "lpush":
                final String lpushKey = options.firstArgument();
                for (String arg : options.getArgumentsAfterFirst()) {
                    if (!quiet) out("pushing "+arg+" on to list "+lpushKey+"\n");
                    redisService.lpush(lpushKey, arg);
                }
                break;

            case "lpop":
                final String lpopKey = options.firstArgument();
                final Integer count = StringUtil.safeParseInt(options.secondArgument());
                for (int i = 0; i < (count == null ? 1 : count); i++) {
                    out(i+": "+redisService.lpop(lpopKey));
                }
                break;

            case "set":
                final String first = options.firstArgument();
                final String second = options.secondArgument();
                redisService.set(first, second);
                if (!quiet) out(first+" = "+second);

            case "keys":
                out(StringUtil.toString(redisService.keys(getKeysArg(options)), "\n"));
                break;

            case "del":
                for (String key : options.getArguments()) {
                    if (!quiet) out("deleting "+key+"\n");
                    redisService.del(key);
                }
                break;

            default:
                err("redis command not yet supported: "+options.getCommand());
                break;
        }
    }

    private String getKeysArg(RedisOptions options) {
        return options.isDisableKeysWildcard() ? options.firstArgument() : "*"+options.firstArgument()+"*";
    }

}
