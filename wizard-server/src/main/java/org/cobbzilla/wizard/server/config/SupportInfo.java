package org.cobbzilla.wizard.server.config;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

public class SupportInfo extends BasicSupportInfo {

    @Getter @Setter private Map<String, BasicSupportInfo> locale = new HashMap<>();

    public BasicSupportInfo forLocale (String loc) {
        final BasicSupportInfo info = locale.get(loc);
        return info == null ? this : info;
    }

}
