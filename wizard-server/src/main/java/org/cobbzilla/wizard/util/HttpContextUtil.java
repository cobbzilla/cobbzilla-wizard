package org.cobbzilla.wizard.util;

import javax.ws.rs.core.MultivaluedMap;

public class HttpContextUtil {

    public static String encodeParams(MultivaluedMap<String, String> params) {
        final StringBuilder b = new StringBuilder();
        if (params != null && !params.isEmpty()) {
            for (String name : params.keySet()) {
                for (String value : params.get(name)) {
                    if (b.length() > 0) b.append("&");
                    b.append(name).append("=").append(value);
                }
            }
        }
        return b.toString();
    }

}
