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

package com.sun.messaging.jmq.util;

import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import com.sun.messaging.jmq.util.selector.*;

/**
 *
 */
public abstract class RuntimeFaultInjection 
{
     private LoggerWrapper logger = null;
     private java.util.logging.Logger jlogger = null;

     private Set injections = null;
     private Map injectionSelectors = null;
     private Map injectionProps = null;

     private String shutdownMsg = "SHUTING DOWN BECAUSE OF" + " FAULT ";
     private String haltMsg = "HALT BECAUSE OF" + " FAULT ";

     public boolean FAULT_INJECTION = false;

     /**
      * @param name the process name
      */
     public void setProcessName(String name) {
         shutdownMsg = "SHUTING DOWN "+name+" BECAUSE OF" + " FAULT ";
         haltMsg = "HALT "+name+" BECAUSE OF" + " FAULT ";
     }

     public RuntimeFaultInjection() {
         injections = Collections.synchronizedSet(new HashSet());
         injectionSelectors = Collections.synchronizedMap(new HashMap());
         injectionProps = Collections.synchronizedMap(new HashMap());
     }

     protected void setLogger(Object l) {
        if (l instanceof com.sun.messaging.jmq.util.LoggerWrapper) {
            logger = (LoggerWrapper)l;
        } else if (l instanceof java.util.logging.Logger) {
            jlogger = (java.util.logging.Logger)l;
        }
    }

     public void setFault(String fault, String selector)
         throws SelectorFormatException
     {
         setFault(fault, selector, null);
     }

     public void setFault(String fault, String selector, Map props)
         throws SelectorFormatException
     {
         logInfo("Setting Fault "+fault+"[ selector=" + selector + "], [props="+props+"]");
         injections.add(fault);
         if (selector != null && selector.length() != 0) {
            // create a selector and insert
            Selector s = Selector.compile(selector);
            injectionSelectors.put(fault, s);
         }
         if (props != null)
            injectionProps.put(fault, props);

     }

     public void unsetFault(String fault) {
         logInfo("Removing Fault " + fault );
         injections.remove(fault);
         injectionSelectors.remove(fault);
         injectionProps.remove(fault);
     }

     static class FaultInjectionException extends Exception
     {
         public String toString() {
             return "FaultInjectionTrace";
         }
     }

     public void setFaultInjection(boolean inject)
     {
         if (FAULT_INJECTION != inject) {
            if (inject) {
                logInfo("Turning on Fault Injection");
            } else {
                logInfo("Turning off Fault Injection");
            }
            FAULT_INJECTION = inject;
         }
     }


     private void logInjection(String fault, Selector sel)
     {
         String str = "Fault Injection: triggered " + fault;
         if (sel != null)
             str += " selector [ " + sel.toString() + "]";

         Exception ex = new FaultInjectionException();
         ex.fillInStackTrace();
         logInfo(str, ex);
     }

     private Map checkFaultGetProps(String fault, Map props)
     {
         if (!FAULT_INJECTION) return null;
         boolean ok = checkFault(fault, props);
         if (!ok) return null;
         Map m = (Map)injectionProps.get(fault);
         if (m == null) m  = new HashMap();
         return m;
     }

     public boolean checkFault(String fault, Map props)
     {
         return checkFault(fault, props, false);              
     }

     private boolean checkFault(String fault, Map props, boolean onceOnly)
     {
         if (!FAULT_INJECTION) return false;
         if (injections.contains(fault))
         {
             Selector s = (Selector)injectionSelectors.get(fault);
             if (s == null) {
                 logInjection(fault, null);
                 if (onceOnly) injections.remove(fault);
                 return true;
             }
             try {
                 boolean match = s.match(props, null); 
                 if (match) {
                     logInjection(fault, s);
                     if (onceOnly) injections.remove(fault);
                     return true;
                 }
                 return false;
             } catch (Exception ex) {
                 logWarn("Unable to apply fault ", ex);
                 return false;
             }
         }

         return false;
     }

     public void checkFaultAndThrowIOException(String value,
                Map props)
          throws IOException
     {
         if (!FAULT_INJECTION) return;
         if (checkFault(value, props)) {
             IOException ex = new IOException("Fault Insertion: "
                   + value);
             throw ex;
         }    
     }

     public void checkFaultAndThrowException(String value,
                Map props, String ex_class)
          throws Exception
     {
         checkFaultAndThrowException(value, props, ex_class, false);
     }

     public void checkFaultAndThrowException(String value,
                Map props, String ex_class, boolean onceOnly)
          throws Exception
     {
         if (!FAULT_INJECTION) return;
         if (checkFault(value, props, onceOnly)) {
             Class c = Class.forName(ex_class);
             Class[] paramTypes = { String.class };
             Constructor cons = c.getConstructor(paramTypes);
             Object[] paramArgs = { "Fault Injection: " +value };
             Exception ex = (Exception)cons.newInstance(paramArgs);
             throw ex;
         }    
     }

     public void checkFaultAndThrowError(String value, Map props)
          throws Error
     {
         if (!FAULT_INJECTION) return;
         if (checkFault(value, props)) {
             // XXX use exclass to create exception
             Error ex = new Error("Fault Insertion: "
                   + value);
             throw ex;
         }    
     }

     public void checkFaultAndExit(String value,
                Map props, int exitCode, boolean nice)
     {
         if (!FAULT_INJECTION) return;
         if (checkFault(value, props)) {
             if (nice) {
                 logInfo(shutdownMsg + value);
                 exit(exitCode);
             } else {
                 logInfo(haltMsg + value);
                 Runtime.getRuntime().halt(exitCode);
             }
         }
     }

     protected abstract void exit(int exitCode);
     protected abstract String sleepIntervalPropertyName();
     protected abstract int sleepIntervalDefault();

     public boolean checkFaultAndSleep(String value, Map props) {
         return checkFaultAndSleep(value, props, false);
     }

     public boolean checkFaultAndSleep(String value, Map props, boolean unsetFaultBeforeSleep)
     {
         if (!FAULT_INJECTION) {
             return false;
         }
         Map p = checkFaultGetProps(value, props);
         if (p == null) {
             return false;
         }
         String str = (String)p.get(sleepIntervalPropertyName());
         int secs = sleepIntervalDefault();
         if (str != null)  {
             try {
                 secs = Integer.parseInt(str);
             } catch (Exception e) {}
         }
         if (secs <= 0) {
             secs = sleepIntervalDefault();
         }
         if (unsetFaultBeforeSleep) {
             unsetFault(value);
         }
         logInfo("BEFORE SLEEP "+secs +"(seconds) BECAUSE OF FAULT "+value);
         try {
             Thread.sleep(secs*1000L);
         } catch (Exception e) {
             logInfo("SLEEP "+secs +"(seconds) FAULT ("+value+
                                    ") interrupted: "+e.getMessage());
         }
         logInfo("AFTER SLEEP "+secs +"(seconds) BECAUSE OF FAULT "+value);
         return true;
     }

     private void logInfo(String msg) {
         logInfo(msg, null);
     }

     protected void logInfo(String msg, Throwable t) {
        if (logger != null) {
            logger.logInfo(msg, t);
        } else if (jlogger != null) {
            if (t == null) {
            jlogger.log(java.util.logging.Level.INFO, msg);
            } else {
            jlogger.log(java.util.logging.Level.INFO, msg, t);
            }
        }
    }

    protected void logWarn(String msg, Throwable t) {
        if (logger != null) {
            logger.logWarn(msg, t);
        } else if (jlogger != null) {
            if (t == null) {
            jlogger.log(java.util.logging.Level.WARNING, msg);
            } else {
            jlogger.log(java.util.logging.Level.WARNING, msg, t);
            }
        }
    }
}     
