package org.cobbzilla.wizard.dao;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.Client;

public interface SearchPreparer {

    SearchRequestBuilder prepare(Client client);

}
