package org.cobbzilla.util.telephony;

import org.cobbzilla.wizard.telephony.SmsUtil;
import org.junit.Test;
import static org.junit.Assert.*;

public class SmsUtilTest {

    private int len(String s) { return s == null ? 0 : s.length(); }

    public static final String[][] TEST_SHORTNAME = {

        /*      0    5    1    5    2    5    3  0    5    1    5    2    5    3  0    5    1    5    2    5    3*/
        { "20", "Jean-Paul",                     null,                            "Jean-Paul" }, // case 1
        { "20", "Jean-Paul",                     "",                              "Jean-Paul" }, // case 1

        /*      0    5    1    5    2    5    3  0    5    1    5    2    5    3  0    5    1    5    2    5    3*/
        { "20", null,                            "Sartre",                        "Sartre" },    // case 2
        { "20", "",                              "Sartre",                        "Sartre" },    // case 2

        /*      0    5    1    5    2    5    3  0    5    1    5    2    5    3  0    5    1    5    2    5    3*/
        { "10", null,                            "Lincoln-Douglas",               "Lincoln-.." },    // case 3

        /*      0    5    1    5    2    5    3  0    5    1    5    2    5    3  0    5    1    5    2    5    3*/
        { "10", "Abraham-Steven",                null,                            "Abraham-.." },    // case 4

        /*      0    5    1    5    2    5    3  0    5    1    5    2    5    3  0    5    1    5    2    5    3*/
        { "20", "Jean-Paul",                     "Sartre",                        "Jean-Paul Sartre" }, //case 5

        /*      0    5    1    5    2    5    3  0    5    1    5    2    5    3  0    5    1    5    2    5    3*/
        { "17", "Jean-Paul",                     "Paul-Sartre",                   "Jean-Paul Paul-.." }, // case 6
        { "15", "Jean-Paul",                     "Sartre",                        "Jean-Paul S." }, // case 7

        /*      0    5    1    5    2    5    3  0    5    1    5    2    5    3  0    5    1    5    2    5    3*/
        { "15", "Abraham-Steven",                "Lincoln",                       "Abraha. Lincoln" }, // case 8

        /*      0    5    1    5    2    5    3  0    5    1    5    2    5    3  0    5    1    5    2    5    3*/
        { "13", "Abraham-Steven",                "Lincoln",                       "A. Lincoln" }, // case 9

        /*      0    5    1    5    2    5    3  0    5    1    5    2    5    3  0    5    1    5    2    5    3*/
        { "10", "Abraham-Steven",                "Lincoln-Douglas",               "A. Linco.." }, // case 10
    };

    @Test
    public void testShortName() throws Exception {
        for (String[] test : TEST_SHORTNAME) {

            final int maxlen = Integer.parseInt(test[0]);
            final String fn = test[1];
            final String ln = test[2];
            final String expected = test[3];

            final String limit = SmsUtil.shortName(fn, ln, maxlen);

            // never exceed maxlen
            assertTrue("failed on expecting: "+expected, limit.length() <= maxlen);

            // if fn + " " + ln would exceed maxlen, no more than 3 spaces should be wasted
            if (len(fn) + len(ln) + 1 > maxlen) assertTrue("failed on expecting: "+expected, maxlen - limit.length() <= 3);

            // ensure result is as expected
            assertEquals("failed on expecting: "+expected, expected, limit);
        }
    }

    public static final String[][] TEST_SHORT = {
        /*      0    5    1    5    2    5    3  0    5    1    5    2    5    3  0    5    1    5    2    5    3*/
        { "20", null,                            "" }, // case 1
        { "20", "short",                         "short" }, // case 2
        { "5",  "long-enough",                   "long-" }, // case 3
        { "5",  "long-enough",                   "long-" }, // case 3
        { "10", "long-enough",                   "long-eno.." }, // case 4
    };

    @Test
    public void testShorten() throws Exception {
        for (String[] test : TEST_SHORT) {

            final int maxlen = Integer.parseInt(test[0]);
            final String s = test[1];
            final String expected = test[2];

            final String limit = SmsUtil.shorten(s, maxlen);

            // never exceed maxlen
            assertTrue("failed on expecting: "+expected, limit.length() <= maxlen);

            // ensure result is as expected
            assertEquals("failed on expecting: "+expected, expected, limit);
        }
    }
}
