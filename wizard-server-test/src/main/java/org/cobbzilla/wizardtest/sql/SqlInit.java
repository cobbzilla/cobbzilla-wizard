package org.cobbzilla.wizardtest.sql;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;

import static org.cobbzilla.util.string.StringUtil.UTF8cs;

@Service @Slf4j
public class SqlInit {

    @Autowired private ApplicationContext applicationContext;
    @Autowired private DataSource dataSource;

    @Getter @Setter private String sqlFiles;

    public void runSql () throws Exception {

        try (final Connection conn = dataSource.getConnection()) {

            for (final String sqlFile : sqlFiles.split(",")) {

                final Resource resource = applicationContext.getResource(sqlFile);
                try (final InputStream in = resource.getInputStream()) {

                    for (final String sql : IOUtils.toString(in, UTF8cs).split(";")) {

                        try (Statement statement = conn.createStatement()) {
                            statement.executeUpdate(sql);
                        }
                    }
                }
            }
        }
    }

}
