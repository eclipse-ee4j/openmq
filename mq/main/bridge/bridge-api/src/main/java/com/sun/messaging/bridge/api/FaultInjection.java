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

package com.sun.messaging.bridge.api;

import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.messaging.jmq.util.RuntimeFaultInjection;
import com.sun.messaging.bridge.api.BridgeBaseContext;

/**
 * All fault target constants start with FAULT_ and
 * only fault target constant starts with FAULT_
 *
 */
public class FaultInjection extends RuntimeFaultInjection
{
     private static BridgeBaseContext _bc = null;

     private Logger _logger = null;

     private static FaultInjection _fault = null;

     /**
      * 1 is before func call 
      * 2 is after func call
      */
     public static final String STAGE_1 = "1";
     public static final String STAGE_2 = "2";

     /*********************************************************************************
      * example usages:
      *
      * imqbridgemgr debug fault -n receive.2 -debug
      * imqbridgemgr debug fault -n xa.prepare.1 -o selector="cfref = 'CF9666'" -debug
      *
      *********************************************************************************/

     /*****************************************************************
      * START of JMS Bridge faults
      *****************************************************************/

     /**
      * faults for transacted links
      *
      * throw XAException(String) on next specified operation with cfref
      ******************************************************************/
     public static final String FAULT_XA_START_1 = "xa.start.1";
     public static final String FAULT_XA_START_2 = "xa.start.2";
     public static final String FAULT_XA_END_1 = "xa.end.1";
     public static final String FAULT_XA_END_2 = "xa.end.2";
     public static final String FAULT_XA_PREPARE_1 = "xa.prepare.1";
     public static final String FAULT_XA_PREPARE_2 = "xa.prepare.2";
     public static final String FAULT_XA_COMMIT_1 = "xa.commit.1";
     public static final String FAULT_XA_COMMIT_2 = "xa.commit.2";
     public static final String FAULT_XA_ROLLBACK_1 = "xa.rollback.1";
     public static final String FAULT_XA_ROLLBACK_2 = "xa.rollback.2";
     public static final String FAULT_XA_RECOVER_1 = "xa.recover.1";

     
     /**
      * faults for both transacted and non-transacted links
      *
      * throw JMSException on next specified operation 
      ***********************************************************/
     public static final String FAULT_RECEIVE_1 = "receive.1";
     public static final String FAULT_RECEIVE_2 = "receive.2";
     public static final String FAULT_TRANSFORM_2 = "transform.2";
     public static final String FAULT_SEND_1 = "send.1";
     public static final String FAULT_SEND_2 = "send.2";

     /**
      * faults for dmq
      *
      * throw JMSException on next specified operation with dmqName 
      **************************************************************/
     public static final String FAULT_DMQ_SEND_1 = "dmq.send.1";
     public static final String FAULT_DMQ_TRANSFORM_2 = "dmq.transform.2";

     /**
      * faults for non-transaced links
      *
      * throw JMSException on next specified operation 
      ******************************************************/
     public static final String FAULT_ACK_1 = "ack.1";
     public static final String FAULT_ACK_2 = "ack.2";
     
     /**
      * fault properties 
      ***********************************************************/
     //for dmq faults
     public static final String DMQ_NAME_PROP = "dmqName";

     //for xa transaction faults
     public static final String CFREF_PROP = "cfref";

     /******************************************************************
      * END of JMS Bridge faults
      ******************************************************************/

     private static final String SLEEP_INTERVAL_PROP = "mqSleepInterval"; //in secs
     private static final int   SLEEP_INTERVAL_DEFAULT = 60;

     /**
      * This method need to be called before constructor
      */
     public static void setBridgeBaseContext(BridgeBaseContext bc) {
         _bc = bc;
     }

     public void setLogger(Logger l) {
         _logger = l;
     }

     public static synchronized FaultInjection getInjection()
     {
         if (_fault == null)
             _fault = new FaultInjection();

         return _fault;
     }

     public FaultInjection() {
         super();
         setProcessName((_bc.isEmbeded() ? "BROKER":"PROCESS"));
     }

     protected void exit(int exitCode) {
         logWarn("EXIST JVM from bridge is not supported", null);
     }

     protected String sleepIntervalPropertyName() {
         return SLEEP_INTERVAL_PROP;
     }

     protected int sleepIntervalDefault() {
         return SLEEP_INTERVAL_DEFAULT;
     }

     @Override
     protected void logInfo(String msg, Throwable t) {
         if (_bc != null) {
             _bc.logInfo(msg, t);
         }

         Logger logger = _logger;
         if (logger != null) {
             if (t == null) {
                 logger.log(Level.INFO, msg);
             } else {
                 logger.log(Level.INFO, msg, t);
             }
         }
     }

     @Override
     protected void logWarn(String msg, Throwable t) {
         if (_bc != null) {
             _bc.logWarn(msg, t);
         }

         Logger logger = _logger;
         if (logger != null) {
             if (t == null) {
                 logger.log(Level.WARNING, msg);
             } else {
                 logger.log(Level.WARNING, msg, t);
             }
         }
     }
}
