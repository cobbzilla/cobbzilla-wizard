package org.cobbzilla.wizardtest.resources;

import lombok.AllArgsConstructor;
import org.cobbzilla.wizard.client.ApiClientBase;

@AllArgsConstructor
public class BasicTestApiClient extends ApiClientBase {

    private AbstractResourceIT test;

    @Override public synchronized String getBaseUri() { return test.getServer().getClientUri(); }

}
