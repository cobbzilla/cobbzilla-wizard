/**
 * Defines Jasypt data types for transparent hibernate encryption
 */
@TypeDefs({

        @TypeDef(name = ENCRYPTED_STRING, typeClass = EncryptedStringType.class,
                 parameters = { @Parameter(name = "encryptorRegisteredName", value = STRING_ENCRYPTOR_NAME) } ),

        @TypeDef(name = ENCRYPTED_INTEGER, typeClass = EncryptedIntegerAsStringType.class,
                parameters = { @Parameter(name = "encryptorRegisteredName", value = INTEGER_ENCRYPTOR_NAME) } ),

        @TypeDef(name = ENCRYPTED_LONG, typeClass = EncryptedLongAsStringType.class,
                parameters = { @Parameter(name = "encryptorRegisteredName", value = LONG_ENCRYPTOR_NAME) } )

})

package org.cobbzilla.wizard.model.crypto;

import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.jasypt.hibernate4.type.EncryptedIntegerAsStringType;
import org.jasypt.hibernate4.type.EncryptedLongAsStringType;
import org.jasypt.hibernate4.type.EncryptedStringType;

import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.*;

