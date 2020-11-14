package org.cobbzilla.wizard.model;

public class BasicConstraintConstants {

    public static final String ERR_HASHED_PASSWORD_EMPTY = "{err.hashedPassword.empty}";
    public static final String ERR_HASHED_PASSWORD_LENGTH = "{err.hashedPassword.length}";

    public static final String ERR_UUID_UNIQUE = "{err.uuid.unique}";
    public static final String ERR_UUID_EMPTY = "{err.uuid.empty}";
    public static final String ERR_UUID_LENGTH = "{err.uuid.length}";

    public static final String ERR_SORT_ORDER_INVALID = "{err.sortOrder.invalid}";

    public static final String ERR_RESET_TOKEN_LENGTH = "{err.resetToken.length}";
    public static final String ERR_BOUNDS_INVALID = "{err.bounds.invalid}";

    public static final int UUID_MAXLEN = 100;
    public static final int HASHEDPASSWORD_MAXLEN = 200;
    public static final int RESETTOKEN_MAXLEN = 30;

    public static final int URL_MAXLEN = 1024;

    public static final String SV_MAJOR_TOO_LARGE = "{err.semanticVersion.major.tooBig}";
    public static final String SV_MAJOR_TOO_SMALL = "{err.semanticVersion.major.tooSmall}";
    public static final String SV_MINOR_TOO_LARGE = "{err.semanticVersion.minor.tooBig}";
    public static final String SV_MINOR_TOO_SMALL = "{err.semanticVersion.minor.tooSmall}";
    public static final String SV_PATCH_TOO_LARGE = "{err.semanticVersion.patch.tooBig}";
    public static final String SV_PATCH_TOO_SMALL = "{err.semanticVersion.patch.tooSmall}";
    public static final String SV_BUILD_TOO_LARGE = "{err.semanticVersion.build.tooBig}";
    public static final String SV_BUILD_TOO_SMALL = "{err.semanticVersion.build.tooSmall}";
    public static final int SV_VERSION_MAXLEN = 30;

    public static final int SV_VERSION_MAX = 999999999;
    public static final int SV_VERSION_MIN = 0;
}
