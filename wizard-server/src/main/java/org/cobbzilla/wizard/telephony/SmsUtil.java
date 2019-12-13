package org.cobbzilla.wizard.telephony;

public class SmsUtil {

    public static String shorten(String text, int max) {
        // case 1: nothing
        if (text == null) return "";

        // case 2: it fits
        if (text.length() <= max) return text;

        // case 3: max is short, return what we can
        if (max <= 5) return text.substring(0, max);

        // case 4: max is long, return what we can and append ..
        return text.substring(0, max-2) + "..";
    }

    public static String shortName(String firstName, String lastName, int max) {

        if (max < 10) throw new IllegalArgumentException("shortName: max must be >= 10");

        final int flen = firstName == null ? 0 : firstName.length();
        final int llen = lastName == null ? 0 : lastName.length();
        final String fn = (firstName == null ? "" : firstName);
        final String ln = (lastName == null ? "" : lastName);

        // case 1: first name fits, no last name
        if (flen <= max && llen == 0) return fn;

        // case 2: last name fits, no first name
        if (llen <= max && flen == 0) return ln;

        // case 3: no first name, return as much of the last name as we can
        if (flen == 0) return ln.substring(0, Math.min(llen, max-2)) + "..";

        // case 4: no last name, return as much of the first name as we can
        if (llen == 0) return fn.substring(0, Math.min(flen, max-2)) + "..";

        // case 5: first and last names both fit
        if (flen+llen+1 <= max) return fn + " " + ln;

        // case 6: first name fits and at least 5+ chars of last name will fit
        // we'll need room for flen + min(llen, 5) + 3 chars
        if (flen + 3 + Math.min(llen, 5) <= max) {
            return fn + " " + ln.substring(0, Math.min(llen, 5)) + "..";
        }

        // case 7: first name fits and at least 1 char of last name will fit
        if (flen + 2 <= max) {
            return fn + " " + ln.charAt(0) + ".";
        }

        // case 8: last name fits and at least 5+ chars of first name will fit
        // we'll need foom for llen + min(flen, 5) + 2 chars
        if (llen + 2 + Math.min(flen, 5) <= max) {
            return fn.substring(0, Math.min(max-llen-2, flen)) + ". " + ln;
        }

        // case 9: last name fits and at least 1 char of first name will fit
        if (llen + 2 <= max) return fn.charAt(0) + ". " + ln;

        // case 10: last name does not fit, show first initial and as much of last name as we can
        return fn.charAt(0) + ". " + ln.substring(0, Math.min(llen, max-5)) + "..";
    }

}
