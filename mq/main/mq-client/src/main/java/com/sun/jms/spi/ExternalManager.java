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
 * @(#)ExternalManager.java	1.4 06/27/07
 */ 

package com.sun.jms.spi;

import java.security.GeneralSecurityException;

public interface ExternalManager {

    /**
     * Returns the printwriter to use for the error log. If not set
     * explicitly then System.err should be returned.
     * @return PrintStream to use for output of error text.
     */
    java.io.PrintWriter getErrorLog();


    /**
     * Returns the printwriter to use for the trace or debug log. If
     * not set explicitly then System.out should be returned.
     * @return PrintStream to use for output of trace or debug text.
     */
    java.io.PrintWriter getOutputLog();


    /**
     * Returns the printwriter to use for the event log. If
     * not set explicitly then System.out should be returned.
     * @return PrintStream to use for output of event text.
     */
    java.io.PrintWriter getEventLog();
    
    /**
     * Authenticate username and password.
     * @throws GeneralSecurityException if authenticate fails.
     */
    public void authenticate(String username, String password)
        throws GeneralSecurityException;

   /**
    * Returns flag indicating if aut-recovery is enabled for distributed
    * transactions. If so then JMS must recover prepared transactions.
    * services for JMS.
    * @return boolean if true then auto-recover 
    */
   public boolean getAutoRecover();





}






