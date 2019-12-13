package org.cobbzilla.wizard.model.entityconfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.Transient;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class ModelMigrationResult {

    @Getter @Setter private int latestApplied = -1;
    @Getter @Setter private int currentRemoteVersion = -1;
    @Getter @Setter private int numApplied = 0;
    public void incrNumApplied() { numApplied++; }

    @Getter @Setter private Set<Integer> alreadyApplied = new HashSet<>();

    @JsonIgnore @Transient public String getMessage() {
        return numApplied == 0
                ? (alreadyApplied.isEmpty() ? "no migrations to apply" : "all "+alreadyApplied.size()+" migrations already applied (latest local version = "+latestAlreadyApplied()+")") + ". remote version = " + currentRemoteVersion
                : "successfully applied " + numApplied + " migrations. remote version = " + latestApplied;
    }

    private Integer latestAlreadyApplied() {
        Integer max = null;
        for (Integer i : alreadyApplied) max = max == null ? i : max < i ? i : max;
        return max;
    }

}
