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

package com.sun.messaging.jmq.jmsserver.persist.jdbc.sharecc;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;

import java.util.List;
import java.util.ArrayList;

/**
 * Factory for DAO object.
 */
public class ShareConfigRecordDAOFactory {

    protected ShareConfigRecordDAO configRecordDAO = null;
    protected List daoList = null;

    public ShareConfigRecordDAO getShareConfigRecordDAO() 
    throws BrokerException {

        if (configRecordDAO == null) {
            configRecordDAO = new ShareConfigRecordDAOImpl();
        }
        return configRecordDAO;
    }

    public List getAllDAOs() throws BrokerException {

        if ( daoList == null ) {
            synchronized( this ) {
                if ( daoList == null ) {
                    ArrayList list = new ArrayList(1);
                    list.add( getShareConfigRecordDAO() );
                    daoList = list;
                }
            }
        }

        return daoList;
    }
}
