package org.cobbzilla.wizardtest.resources;

import org.cobbzilla.restex.targets.TemplateCaptureTarget;
import org.cobbzilla.wizard.client.script.ApiRunnerListenerBase;
import org.cobbzilla.wizard.client.script.ApiScript;

import java.util.Map;

public class ApiDocsApiRunnerListener extends ApiRunnerListenerBase {

    private TemplateCaptureTarget apiDocs;

    @SuppressWarnings("unused") // called from ApiRunner copy constructor via reflection (which is called from ApiScriptMultiDriver.run)
    public ApiDocsApiRunnerListener(ApiDocsApiRunnerListener other) {
        super(other.getName());
        this.apiDocs = other.apiDocs;
    }

    public ApiDocsApiRunnerListener(String name, TemplateCaptureTarget apiDocs) {
        super(name);
        this.apiDocs = apiDocs;
    }

    @Override public void beforeCall(ApiScript script, Map<String, Object> ctx) {
        apiDocs.addNote(script.getComment());
    }

}
