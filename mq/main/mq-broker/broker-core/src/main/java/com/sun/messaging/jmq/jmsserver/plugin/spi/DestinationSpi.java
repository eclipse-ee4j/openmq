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

package com.sun.messaging.jmq.jmsserver.plugin.spi;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.util.selector.SelectorFormatException;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.ProducerUID;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

public interface DestinationSpi { 

    public DestinationUID getDestinationUID();
    public String getUniqueName();
    public boolean isQueue();
    public String getDestinationName();

    public int getType();

    public boolean isDMQ();
    public boolean isTemporary();
    public boolean isInternal();
    public boolean isAutoCreated();

    public long getBytesProducerFlow(); 
    public int getSizeProducerFlow(); 
    public boolean isProducerActive(ProducerUID uid);

    public boolean addProducer(ProducerSpi p)
    throws BrokerException;

    public void removeConsumer(ConsumerUID interest, boolean notify)
    throws BrokerException;


    public int getActiveConsumerCount(); 

    public int getFailoverConsumerCount();

    public ConnectionUID getConnectionUID();   

    public int getMaxPrefetch(); 

    public void setValidateXMLSchemaEnabled(boolean b);

    public boolean validateXMLSchemaEnabled();

    public void setXMLSchemaUriList(String s); 

    public String getXMLSchemaUriList(); 

    public void setReloadXMLSchemaOnFailure(boolean b); 
    public boolean reloadXMLSchemaOnFailure(); 
}
