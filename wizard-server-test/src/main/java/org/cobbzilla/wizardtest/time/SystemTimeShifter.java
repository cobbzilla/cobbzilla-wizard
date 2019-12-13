package org.cobbzilla.wizardtest.time;

import mockit.Mock;
import mockit.MockClass;
import mockit.Mockit;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static org.cobbzilla.util.daemon.ZillaRuntime.now;

/**
 * Found at: http://virgo47.wordpress.com/2012/06/22/changing-system-time-in-java/
 *
 * Class changes the system time returned by {@link System#currentTimeMillis()} via JMockit weaving.
 * <p/>
 * Original System class can be restored any time calling {@link #reset()} method. There are a few ways how to specify modified system time:
 * <ul>
 * <li>setting ms offset via {@link #setOffset(long)}
 * <li>changing ms offset (relatively) via {@link #changeOffset(long)}
 * <li>setting new date, time or ISO date/time via {@link #setIsoDate(String)}
 * </ul>
 * <p/>
 * Any of these methods can be used through system properties (-D) this way (first property in this order is used, others ignored):
 * <ul>
 * <li>{@code -Dsystime.offset=1000} - shift by one second to the future (negative number can be used)
 * <li>{@code -Dsystime.millis=1000} - set system time one second after start of the era (1970...)
 * <li>{@code -Dsystime.iso=2000-01-01T00:00:47} - 47 seconds after beginning of the 2000, alternatively you can set only time (00:00:47, date stays current) or
 * only date (2000-01-01, current time) without 'T' in both cases.
 * </ul>
 * <p/>
 * There must be something that causes class load, otherwise nothing happens. In order to allow this without modifying the original program, one may use this
 * class as a main class with original main class as the first argument (they will be correctly shifted when served to the original class). If no relevant
 * property is specified via -D, nothing happens. In any case (programmatic or main class replacement) this class has to be on a classpath. For application
 * server usage this means it has to be in its system libraries, not in EAR/WAR that is not loaded during the AS start yet.
 * <p/>
 * Example:
 *
 * <pre>
 * java -Dsystime.iso=2000-01-01T00:00:47 SystemTimeShifter my.uber.appserver.Main arg1 second "third long with spaces"
 * </pre>
 * <b>WARNING:</b> Sun/Oracle HotSpot JVM and its inline optimization may mess up with the mock after it is set up, so if you notice that the time
 * returns to normal after number of invocations, you should add {@code -XX:-Inline} option to your java command line. Other JVM specific options
 * may be needed for different JVM implementations.
 *
 * @author <a href="mailto:virgo47@gmail.com">Richard "Virgo" Richter</a>
 */
public class SystemTimeShifter {
    /**
     * System property setting ms offset.
     */
    public static final String PROPERTY_OFFSET = "systime.offset";

    /**
     * System property setting "current" millis.
     */
    public static final String PROPERTY_MILLIS = "systime.millis";

    /**
     * System property setting ISO date/time (or date, or time).
     */
    public static final String PROPERTY_ISO_DATE = "systime.iso";

    private static final long INIT_MILLIS = now();
    private static final long INIT_NANOS = System.nanoTime();
    private static long offset;

    private static boolean mockInstalled;

    @Deprecated
    protected SystemTimeShifter() {
        // prevents calls from subclass
        throw new UnsupportedOperationException();
    }

    static {
        String isoDate = System.getProperty(PROPERTY_ISO_DATE);
        String millis = System.getProperty(PROPERTY_MILLIS);
        String offset = System.getProperty(PROPERTY_OFFSET);
        try {
            if (isoDate != null) {
                setIsoDate(isoDate);
            } else if (millis != null) {
                setMillis(Integer.parseInt(millis));
            } else if (offset != null) {
                setOffset(Integer.parseInt(offset));
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    /**
     * Bootstrap main to allow time shifting before actually loading the real main class. Real
     * main class must be the first argument, it will be removed from the list when calling the
     * real class. Without using any relevant -D property there will be no time shifting.
     *
     * @param args argument list with original (desired) class as the first argument
     * @throws Exception may happen during the reflection call of the other main
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws Exception {
        String[] newArgs = new String[args.length - 1];
        System.arraycopy(args, 1, newArgs, 0, args.length - 1);

        Class clazz = Class.forName(args[0]);
        Method main = clazz.getMethod("main", newArgs.getClass());
        main.invoke(null, (Object) newArgs);
    }

    /**
     * Sets the new "system" time to specified ISO time. It is possible to set exact time with the format {@code yyyy-MM-dd'T'HH:mm:ss} (no apostrophes around T
     * in the actual string!) or one can set just time
     * (then current date stays) or just date (then current time stays).
     * <p/>
     * If parse fails for whatever reason, nothing is changed.
     *
     * @param isoDate String with ISO date (date+time, date or just time)
     */
    public static synchronized void setIsoDate(String isoDate) {
        try {
            if (isoDate.indexOf('T') != -1) { // it's date and time (so "classic" ISO timestamp)
                long wantedMillis = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(isoDate).getTime();
                offset = wantedMillis - millisSinceClassInit() - INIT_MILLIS;
            } else if (isoDate.indexOf(':') != -1) { // it's just time we suppose
                Calendar calx = Calendar.getInstance();
                calx.setTime(new SimpleDateFormat("HH:mm:ss").parse(isoDate));

                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, calx.get(Calendar.HOUR_OF_DAY));
                cal.set(Calendar.MINUTE, calx.get(Calendar.MINUTE));
                cal.set(Calendar.SECOND, calx.get(Calendar.SECOND));
                offset = cal.getTimeInMillis() - millisSinceClassInit() - INIT_MILLIS;
            } else { // it must be just date then!
                Calendar calx = Calendar.getInstance();
                calx.setTime(new SimpleDateFormat("yyyy-MM-dd").parse(isoDate));

                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.DAY_OF_MONTH, calx.get(Calendar.DAY_OF_MONTH));
                cal.set(Calendar.MONTH, calx.get(Calendar.MONTH));
                cal.set(Calendar.YEAR, calx.get(Calendar.YEAR));
                offset = cal.getTimeInMillis() - millisSinceClassInit() - INIT_MILLIS;
            }
            mockSystemClass();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets ms offset against current millis (not against real, instead changes current value relatively).
     *
     * @param offset relative ms offset against "current" millis
     */
    public static synchronized void changeOffset(long offset) {
        SystemTimeShifter.offset += offset;
        mockSystemClass();
    }

    /**
     * Sets ms offset against real millis (rewrites previous value).
     *
     * @param offset new absolute ms offset against real millis
     */
    public static synchronized void setOffset(long offset) {
        SystemTimeShifter.offset = offset;
        mockSystemClass();
    }

    /**
     * Sets current millis to the specified value.
     *
     * @param timestamp new value of "current" millis
     */
    public static synchronized void setMillis(long timestamp) {
        offset = timestamp - INIT_MILLIS;
        mockSystemClass();
    }

    /**
     * Resets the whole System time shifter and removes all JMockit stuff. Real system call is restored.
     */
    public static synchronized void reset() {
        Mockit.tearDownMocks(System.class);
        mockInstalled = false;
        offset = 0;
        System.out.println("Current time millis mock REMOVED");
    }

    private static void mockSystemClass() {
        if (!mockInstalled) {
            Mockit.setUpMock(SystemMock.class);
            System.out.println("Current time millis mock INSTALLED: " + new Date());
            mockInstalled = true;
        } else {
            System.out.println("Current time millis mock probably INSTALLED previously: " + new Date());
        }
    }

    public static boolean isMockInstalled() {
        return mockInstalled;
    }

    /**
     * Handy if you set up the mock by some other means like {@link Mockit#setUpStartupMocks(Object...)}.
     *
     * @param mockInstalled true if you want to pretend that the mock is already in place (or is/will be installed otherwise)
     */
    public static void setMockInstalled(boolean mockInstalled) {
        SystemTimeShifter.mockInstalled = mockInstalled;
    }

    /**
     * Returns real time millis based on nano timer difference (not really a call to {@link System#currentTimeMillis()}.
     *
     * @return real time millis as close as possible
     */
    public static long currentRealTimeMillis() {
        return INIT_MILLIS + millisSinceClassInit();
    }

    private static long millisSinceClassInit() {
        return (System.nanoTime() - INIT_NANOS) / 1000000;
    }

    @MockClass(realClass = System.class)
    public static class SystemMock {
        /**
         * Fake current time millis returns value modified by required offset.
         *
         * @return fake "current" millis
         */
        @Mock
        public static long currentTimeMillis() {
            return INIT_MILLIS + offset + millisSinceClassInit();
        }
    }
}