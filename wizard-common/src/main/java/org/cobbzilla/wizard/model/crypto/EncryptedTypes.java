package org.cobbzilla.wizard.model.crypto;

import org.apache.commons.lang.ArrayUtils;
import org.hibernate.annotations.Type;

import java.lang.reflect.Field;

public class EncryptedTypes {

    public static final int ENC_PAD  = 100;
    public static final int ENC_INT  = 50;
    public static final int ENC_LONG = 50;

    public static final String ENCRYPTED_STRING = "encryptedString";
    public static final String STRING_ENCRYPTOR_NAME = "hibernateStringEncryptor";

    public static final String ENCRYPTED_INTEGER = "encryptedInteger";
    public static final String INTEGER_ENCRYPTOR_NAME = "hibernateIntegerEncryptor";

    public static final String ENCRYPTED_LONG = "encryptedLong";
    public static final String LONG_ENCRYPTOR_NAME = "hibernateLongEncryptor";

    public static final String[] ENCRYPTED_TYPES = {
      ENCRYPTED_STRING, ENCRYPTED_LONG, ENCRYPTED_INTEGER
    };

    public static boolean isEncryptedType (String t) { return ArrayUtils.contains(ENCRYPTED_TYPES, t); }

    public static boolean isEncryptedField(Field f) {
        final Type t = f.getAnnotation(Type.class);
        return t != null && isEncryptedType(t.type());
    }

}
