package org.cobbzilla.wizard.main;

import org.cobbzilla.util.main.BaseMain;
import org.cobbzilla.wizard.model.anon.AnonScrubber;
import org.cobbzilla.wizard.model.anon.AnonTable;

import java.util.List;

import static org.cobbzilla.util.json.JsonUtil.json;

public class AnonScrubMain<OPT extends AnonScrubOptions> extends BaseMain<OPT> {

    @Override protected void run() throws Exception {

        final OPT options = getOptions();
        final List<AnonTable> tables = options.getScrubs();

        if (options.isDumpJson()) {
            out(json(tables));

        } else {
            final AnonScrubber scrubber = new AnonScrubber().setTables(tables);
            scrubber.anonymize(options.getDatabaseReadConfiguration(),
                    options.getDatabaseWriteConfiguration(),
                    options.isIgnoreUnknown());
        }
    }

}
