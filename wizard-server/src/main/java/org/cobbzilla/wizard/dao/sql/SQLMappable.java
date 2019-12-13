package org.cobbzilla.wizard.dao.sql;

import java.util.Map;

public interface SQLMappable {

    Map<String, SQLFieldTransformer> getSQLFieldTransformers();

}
