package org.cobbzilla.wizard.model.search;

import lombok.*;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(of={"sortField", "sortOrder", "func"})
public class SearchSort {

    @Getter @Setter private String sortField;
    @Getter @Setter private SortOrder sortOrder = SortOrder.ASC;
    @Getter @Setter private String func;
    public boolean hasFunc () { return !empty(func); }

    public SearchSort(String sortField, SortOrder sortOrder) {
        this.sortField = sortField;
        this.sortOrder = sortOrder;
    }

    public SearchSort(String sort) {
        if (sort.startsWith("+") || sort.startsWith(" ")) {
            sortField = sort.substring(1).trim();
            sortOrder = SortOrder.ASC;
        } else if (sort.startsWith("-")) {
            sortField = sort.substring(1).trim();
            sortOrder = SortOrder.DESC;
        } else if (sort.endsWith("+") || sort.endsWith(" ")) {
            sortField = sort.substring(0, sort.length()-1).trim();
            sortOrder = SortOrder.ASC;
        } else if (sort.endsWith("-")) {
            sortField = sort.substring(0, sort.length()-1).trim();
            sortOrder = SortOrder.DESC;
        } else {
            sortField = sort.trim();
            sortOrder = SortOrder.ASC;
        }
    }

}
