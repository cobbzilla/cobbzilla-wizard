package org.cobbzilla.wizardtest;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;

import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.RANDOM;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;

public class RandomUtil {

    public static final String TEST_EMAIL_SUFFIX = "@example.com";

    public static String randomName() { return randomName(20); }

    public static String randomName(String prefix) { return prefix + "_" + randomName(20); }

    public static String randomName(int length) { return RandomStringUtils.randomAlphanumeric(length); }

    public static String randomDigits(int length) { return RandomStringUtils.randomNumeric(length); }

    public static String randomEmail (String prefix) { return prefix + "-" + randomName(5) + now() + TEST_EMAIL_SUFFIX; }

    public static String randomEmail () { return randomName(5) + now() + TEST_EMAIL_SUFFIX; }

    public static String randomEmail (int length) {
        final String baseEmail = now() + TEST_EMAIL_SUFFIX;
        final int emailLength = Math.max(10, length - baseEmail.length());
        return randomName(emailLength) + baseEmail;
    }

    public static long randomTime(int minDaysInFuture) {
        int days = minDaysInFuture + RANDOM.nextInt();
        return now() + (days * 1000 * 60 * 60 * 24);
    }

    public static boolean randomBoolean() { return RandomUtils.nextInt(0, 2) % 2 == 0; }

    public static <T> T pickMod(T[] things, int i) {
        if (i <= 0 || i > things.length) i = things.length;
        return things[things.length % i];
    }
    public static <T> T pickMod(List<T> things, int i) {
        if (i <= 0 || i > things.size()) i = things.size();
        return things.get(things.size() % i);
    }

}
