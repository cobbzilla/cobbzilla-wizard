package org.cobbzilla.wizard.server.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.map.DefaultedMap;

import java.util.Map;

public class SupportInfo extends BasicSupportInfo {

    private SupportInfo self;

    public SupportInfo () { self = this; }

    @Getter @Setter private Map<String, BasicSupportInfo> locale = new DefaultedMap<>(k -> self);

    public BasicSupportInfo forLocale (String loc) { return locale.get(loc); }

}
