package org.cobbzilla.wizard.asset;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

@NoArgsConstructor @AllArgsConstructor @Slf4j
public class AssetStream implements Closeable {

    @Getter @Setter private String uri;
    @Getter @Setter private InputStream stream;
    @Getter @Setter private String contentType;

    public final static String[][] FORMAT_MAP = {
            {"png", "png"},
            {"jpg", "jpg"},
            {"jpeg", "jpg"},
            {"gif", "gif"},
            {"pdf", "pdf"}
    };
    @JsonIgnore public String getFormatName() {
        for (String[] format : FORMAT_MAP) {
            if (contentType.contains("/"+format[0])) return format[1];
        }
        return null;
    }

    @Override public void close() throws IOException {
        if (stream != null) try { stream.close(); } catch (Exception e) { log.warn("close: "+e); }
    }
}
