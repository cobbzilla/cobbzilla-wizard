package org.cobbzilla.wizard.task;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

public class TaskId {

    @Getter @Setter private String uuid;
    public void initUUID () { this.uuid = UUID.randomUUID().toString(); }

}
