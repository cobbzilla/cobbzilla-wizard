package org.cobbzilla.wizard.server.config;

import lombok.*;

@NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper=false)
@ToString
public class ThriftConfiguration {

    @Getter @Setter private int port;
    @Getter @Setter private String service;
    @Getter @Setter private String handler;

}
