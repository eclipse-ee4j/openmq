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

package com.sun.messaging.jms.ra;

import java.util.Properties;

/**
 *  DirectConnectionMetaData encapsulates JMS ConnectionMetaData for MQ DIRECT
 *  mode operation.
 */
public class DirectConnectionMetaData extends com.sun.messaging.jms.ra.ConnectionMetaData {

    /**
     *  Holds the configuration properties of the JMSConnection
     */
    //private Properties connectionProps;
    
    /** Creates a new instance of DirectConnectionMetaData */
    public DirectConnectionMetaData(Properties connectionProps) {
        super(connectionProps);
        //this.connectionProps = connectionProps;
    }

    public boolean hasJMSXAppID(){
        if (this.connectionProps != null){
            return Boolean.parseBoolean(
                (String)(connectionProps.get("imqSetJMSXAppID")));
        }
        return false;
    }

    public boolean hasJMSXUserID(){
        if (this.connectionProps != null){
            return Boolean.parseBoolean((String)(connectionProps.get("imqSetJMSXUserID")));
        }
        return false;
    }

    public boolean hasJMSXProducerTXID(){
        if (this.connectionProps != null){
            return Boolean.parseBoolean(
                (String)(connectionProps.get("imqSetJMSXProducerTXID")));
        }
        return false;
    }

    public boolean hasJMSXConsumerTXID(){
        if (this.connectionProps != null){
            return Boolean.parseBoolean(
                (String)(connectionProps.get("imqSetJMSXConsumerTXID")));
        }
        return false;
    }

    public boolean hasJMSXRcvTimestamp(){
        if (this.connectionProps != null){
            return Boolean.parseBoolean(
                (String)(connectionProps.get("imqSetJMSXRcvTimestamp")));
        }
        return false;
    }
}
