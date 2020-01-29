package org.cobbzilla.wizard.model.search;

import lombok.*;

@NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(of={"sortField", "sortOrder"})
public class SearchSort {

    @Getter @Setter private String sortField;
    @Getter @Setter private SortOrder sortOrder = SortOrder.ASC;

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
