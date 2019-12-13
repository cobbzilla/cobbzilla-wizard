package org.cobbzilla.wizard.model.search;

import org.cobbzilla.wizard.model.HasRelatedEntities;
import org.cobbzilla.wizard.model.RelatedEntities;

public interface SqlViewSearchResult extends HasRelatedEntities {

    RelatedEntities getRelated();

}
