/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

/*
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc.comm;

import java.util.Iterator;
import java.util.HashMap;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.Logger;

public class TableSchema {
    private String tableName = null;

    protected String tableSQL = null;
    protected String afterCreateSQL = null;
    protected String afterDropSQL = null;

    private HashMap indexMap = new HashMap();

    protected TableSchema(String name, String sql) {
        tableName = name;
        tableSQL = sql;
    }

    protected void addIndex(String name, String sql) {
        if (sql != null && sql.trim().length() > 0) {
            indexMap.put(name, sql);
        } else {
            Globals.getLogger().log( Logger.WARNING,
                "SQL command to create the table index " + name +
                " for table " + tableName + " is null or empty" );
        }
    }

    protected void setAfterCreateSQL(String sql) {
        afterCreateSQL = sql;
    }

    protected void setAfterDropSQL(String sql) {
        afterDropSQL = sql;
    }

    protected String getIndex(String name) {
        return (String)indexMap.get(name);
    }

    protected Iterator indexIterator() {
        return indexMap.keySet().iterator();
    }
}
