package org.cobbzilla.wizard.model.support;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Schema
public class SupportInfo extends BasicSupportInfo {

    @JsonIgnore @Getter @Setter private Map<String, BasicSupportInfo> locale = new HashMap<>();

    public BasicSupportInfo forLocale (String loc) {
        final BasicSupportInfo info = locale.get(loc);
        return info == null ? this : info;
    }

}
