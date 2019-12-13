package org.cobbzilla.wizard.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.io.TempDir;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.util.xml.XPathUtil;
import org.w3c.tidy.Tidy;

import javax.persistence.Transient;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.io.FileUtil.toFileOrDie;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.StringUtil.UTF8cs;

@AllArgsConstructor @EqualsAndHashCode(of={"status","json","location"})
public class RestResponse {

    public static Integer defaultWriteToFileLimit = 500;
    public static File defaultLogDir = new TempDir();

    @JsonIgnore @Transient public Integer writeToFileLimit;
    @JsonIgnore @Transient public File logDir;

    public int status;
    public String json;
    public byte[] bytes;
    public String location;

    public RestResponse(int status) { this.status = status; }

    public RestResponse(int statusCode, String responseJson, String locationHeader) {
        this.status = statusCode;
        this.json = responseJson;
        this.location = locationHeader;
    }

    public RestResponse(int statusCode, byte[] responseBytes, String locationHeader) {
        this.status = statusCode;
        this.bytes = responseBytes;
        this.location = locationHeader;
    }

    public RestResponse(HttpResponseBean response) {
        this.status = response.getStatus();
        this.bytes = response.getEntity();
        this.json = response.getEntityString();
        this.headers = new ArrayList<>();
        for (NameAndValue h : response.getHeaders()) this.headers.add(new RestResponseHeader(h));
    }

    public String getLocationUuid () { return location == null ? null : location.substring(location.lastIndexOf("/")+1); }

    public boolean isSuccess () { return isSuccess(status); }
    public boolean isInvalid () { return isInvalid(status); }

    public static boolean isSuccess (int code) { return code/100 == 2; }
    public static boolean isInvalid (int code) { return code == 422; }

    public List<RestResponseHeader> headers;
    public void addHeader(String name, String value) {
        if (headers == null) headers = new ArrayList<>();
        headers.add(new RestResponseHeader(name, value));
    }
    public String header (String name) {
        if (headers != null) {
            for (RestResponseHeader header : headers) {
                if (header.getName().equalsIgnoreCase(name)) return header.getValue();
            }
        }
        return null;
    }
    public int intHeader (String name) { return Integer.parseInt(header(name)); }
    public boolean hasHeader (String name) { return !empty(header(name)); }

    public <T> T jsonObject (Class<T> clazz) { return json(json, clazz); }

    public String bytesAsString () { return new String(bytes, UTF8cs); }

    @Override public String toString() {
        File jsonFile;
        String displayJson = json;
        if (!empty(json)) {
            if ((writeToFileLimit != null && json.length() > writeToFileLimit)
                    || (defaultWriteToFileLimit != null && json.length() > defaultWriteToFileLimit)) {
                int limit = writeToFileLimit != null ? writeToFileLimit : defaultWriteToFileLimit;
                jsonFile = new File(logDir != null ? logDir : defaultLogDir, "restResponse" + hashCode() + ".json");
                if (!jsonFile.exists()) toFileOrDie(jsonFile, json);
                displayJson = json.substring(0, limit) + " ... (full JSON: " + jsonFile + ")";
            }
        }
        return "RestResponse{" +
                "status=" + status +
                (!empty(bytes) ? ", bytes=" + bytes.length : (!empty(json) ? ", json='" + displayJson + '\'' : "")) +
                (!empty(location) ? ", location='" + location + '\'' : "") +
                (!empty(headers) ? ", headers=" + StringUtil.toString(headers, ", ") : "") +
                '}';
    }

    public String shortString () {
        if (!empty(json)) return json;
        return toString();
    }

    public String xpath (String xpath) throws Exception {
        final XPathUtil xp = new XPathUtil(xpath);
        final Tidy tidyConfig = getTidy();
        xp.setTidy(tidyConfig);
        if (!json.startsWith("<?xml ")) {
            return xp.getFirstMatchText("<?xml version=\"1.1\"?>\n"+json);
        }
        return xp.getFirstMatchText(json);
    }

    public boolean contains (String s) { return json != null && json.contains(s); }

    protected Tidy getTidy() {
        final Tidy tidyConfig = new Tidy();
        tidyConfig.setXmlOut(true);
        tidyConfig.setNumEntities(true);
        return tidyConfig;
    }

}
