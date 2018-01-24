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
 * @(#)JMSManager.java	1.5 06/28/07
 */ 

package com.sun.jms.spi;
import javax.jms.JMSException;

/**
 * Interface definition to allow external control of the JMS Server. 
 * Typically this would be the J2EE Server. The intention is to support 
 * pluggable JMS Services.
 */

 
public interface JMSManager {

    /**
     * Set an instance of the external manager.
     * @param externalManager JMS manager.
     */
    void setExternalManager(ExternalManager externalManager);
    

    /**
    * Get an instance of the external manager.
    * @return manager set by a previous set call.
    */
    ExternalManager getExternalManager();


    /**
     * Start an instance of the JMS Service within the
     * current java virtual machine.
     * @exception JMSException thrown if start not successful.
     */
    void startJMSService() throws JMSException;


    /**
     * Stop the instance of the JMS Service running
     * within the current java virtual machine.
     * @exception JMSException thrown if stop not successful.
     */
    void stopJMSService() throws JMSException;
}


