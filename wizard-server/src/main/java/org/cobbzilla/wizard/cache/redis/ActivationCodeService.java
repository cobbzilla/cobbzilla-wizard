package org.cobbzilla.wizard.cache.redis;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.string.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service @Slf4j
public class ActivationCodeService {

    @Autowired @Getter @Setter private RedisService redis;

    public String peek(String key) { return redis.get_plaintext(key); }

    public boolean attempt(String key, String claimant) {
        try {
            final Long remaining = redis.decr(key);
            if (remaining == null || remaining < 0) return false;
            redis.lpush(getClaimantsKey(key), claimant);
            return true;

        } catch (Exception e) {
            log.warn("attempt("+key+") error: "+e);
            return false;
        }
    }

    public void define (String key, int quantity, long expirationSeconds) {
        redis.set_plaintext(key, String.valueOf(quantity), "NX", "EX", expirationSeconds);
    }

    public List<String> getClaimants (String key) { return redis.list(getClaimantsKey(key)); }

    private String getClaimantsKey(String key) { return key+"_claimed"; }

    /**
     * @param args [0] = key; [1] = quantity; [2] = expiration (# days); [3] = redis key (optional)
     */
    public static void main (final String[] args) {

        final RedisService redis = new RedisService();
        final String redisKey = (args.length == 4) ? args[3] : null;

        redis.setConfiguration(() -> new RedisConfiguration(redisKey));

        final ActivationCodeService acService = new ActivationCodeService();
        acService.setRedis(redis);

        final String key = args[0];

        if (args.length > 1) {
            final int quantity = Integer.parseInt(args[1]);
            final long expirationSeconds = Integer.parseInt(args[2]) * TimeUnit.DAYS.toSeconds(1);

            acService.define(key, quantity, expirationSeconds);
            System.out.println("successfully defined key: " + key);

        } else {
            System.out.println("key: " + key);
            System.out.println("remaining: " + acService.peek(key));
            System.out.println("claimants: " + StringUtil.toString(acService.getClaimants(key), ", "));
        }
    }

}
