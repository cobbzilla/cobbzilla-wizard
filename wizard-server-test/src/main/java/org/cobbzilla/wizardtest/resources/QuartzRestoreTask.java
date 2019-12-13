package org.cobbzilla.wizardtest.resources;

import lombok.AllArgsConstructor;
import lombok.Cleanup;

import java.io.*;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.io.FileUtil.temp;

@AllArgsConstructor
public class QuartzRestoreTask implements AbstractResourceIT.PreRestoreTask {
    private final String newSchedName;

    @Override public File handle(File dbDump) {
        final File out = temp(dbDump.getName(), ".sql", dbDump.getParentFile());
        final StringBuilder cronTriggers = new StringBuilder();
        try {
            @Cleanup final BufferedReader reader = new BufferedReader(new FileReader(dbDump));
            @Cleanup final BufferedWriter writer = new BufferedWriter(new FileWriter(out));
            String line;
            boolean subst = false;
            boolean inCronTriggers = false;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("COPY qrtz_")) {
                    if (line.startsWith("COPY qrtz_cron_triggers")) {
                        inCronTriggers = true;
                    } else {
                        inCronTriggers = false;
                    }
                    subst = true;

                } else if (subst) {
                    if (line.equals("\\.")) {
                        subst = false;
                    } else {
                        int tabPos = line.indexOf('\t');
                        line = newSchedName + line.substring(tabPos);
                    }
                }
                if (inCronTriggers) {
                    cronTriggers.append(line).append("\n");
                } else {
                    writer.write(line + "\n");
                }
            }
            writer.write(cronTriggers.toString());

        } catch (Exception e) {
            die("handle: error reading/writing: "+e, e);
        }
        return out;
    }

}
