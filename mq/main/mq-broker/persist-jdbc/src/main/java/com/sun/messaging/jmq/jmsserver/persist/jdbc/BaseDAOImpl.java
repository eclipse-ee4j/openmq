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
 * @(#)BaseDAOImpl.java	1.12 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.CommDBManager;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.CommBaseDAOImpl;
import com.sun.messaging.jmq.util.log.Logger;

import java.sql.*;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;

/**
 * The DAO base class which provides methods for creating and dropping table.
 */
public abstract class BaseDAOImpl extends CommBaseDAOImpl implements DBConstants {

    protected CommDBManager getDBManager() throws BrokerException {
        return DBManager.getDBManager();
    }

    protected void closeSQLObjects( ResultSet rs, Statement stmt,
                                    Connection conn, Throwable ex )
                                    throws BrokerException {
        Util.close( rs, stmt, conn, ex );
    }
}

