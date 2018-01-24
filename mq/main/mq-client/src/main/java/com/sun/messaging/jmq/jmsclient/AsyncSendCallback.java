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

package com.sun.messaging.jmq.jmsclient;

import java.io.PrintStream;
import java.util.logging.Level;
import javax.jms.Message;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.CompletionListener;
import com.sun.messaging.jmq.io.ReadOnlyPacket;
import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;

public class AsyncSendCallback implements Traceable {

     protected MessageProducerImpl producer = null;
     private Destination destination = null;
     private long transactionID = -1L;

     private Message message;
     private CompletionListener completionListener;
     private Message foreignMessage;

     private boolean onAckWait = false; 
     private boolean sendSuccessReturn = false;
     private boolean sendReturned = false;
     private boolean completed = false;
     private Exception exception = null;
     private boolean callbackCalled = false;
     private long timeoutTime = 0L; //only accessed by CB processor thread
     private static final Exception timedoutEx = getTimedoutException();


     private static Exception getTimedoutException() {
         String emsg = AdministeredObject.cr.getKString(
                       ClientResources.X_ASYNC_SEND_COMPLETION_WAIT_TIMEOUT);
         return new JMSException(emsg,
                    ClientResources.X_ASYNC_SEND_COMPLETION_WAIT_TIMEOUT);
     }

     public AsyncSendCallback(MessageProducerImpl p, Destination d,
                              Message m, CompletionListener l, Message fm) {
         this.producer = p;
         this.destination = d;
         this.message = m;
         this.completionListener = l;
         this.foreignMessage = fm;
     }

     protected void startTimeoutTimer() {
         if (timeoutTime != 0L) {
             return;
         }
         timeoutTime = System.currentTimeMillis()+
             producer.session.connection.getAsyncSendCompletionWaitTimeout();
     }

     protected boolean isTimedout() {
         synchronized(this) {
             if (completed || exception != null) {
                 return false;
             }
         }
         if (timeoutTime == 0L) {
             return false; 
         }
         return (System.currentTimeMillis() >= timeoutTime);
     }
    

     protected synchronized void setTransactionID(long tid) {
         transactionID = tid;
     }

     protected synchronized void asyncSendStart() throws JMSException {
         onAckWait = true;
     }

     protected synchronized void sendSuccessReturn() {
         sendSuccessReturn = true;
     }

     protected synchronized boolean hasSendReturned() {
         return sendReturned;
     }

     protected void sendReturn() {
         boolean remove = false;
         synchronized(this) {
             if (!sendSuccessReturn) {
                 remove = true;
             }
         }
         if (remove) {
             producer.session.removeAsyncSendCallback(this);
         }
         synchronized(this) {
             sendReturned = true;
         }
         producer.session.asyncSendCBProcessor.wakeup();
     }

     protected void processCompletion(ReadOnlyPacket ack, boolean checkstatus) {
         Exception ex = null;
         if (checkstatus) {
             ProtocolHandler ph = producer.session.protocolHandler;  
             try {
                 ph.checkWriteJMSMessageStatus(ph.getReplyStatus(ack), 
                     (com.sun.messaging.Destination)destination, ack, ph);
             } catch (Exception e) {
                 ex = e;
             }
         }
         boolean notify = true;
         synchronized(this) {
             if (exception != null && ex == null) {
                 producer.sessionLogger.log(Level.INFO,  
                     "Async send completed: "+this.toString(true));
             }
             if (completed || exception != null) {
                 notify = false;
             } else {
                 if (ex == null) {
                     completed = true;
                 } else {
                     exception = ex;
                     producer.sessionLogger.log(Level.INFO,
                         "Async send exceptioned: "+this.toString(true), ex);
                 }
             } 
         }
         if (notify) {
             if (completed && foreignMessage != null) {
                 try {
                     producer.resetForeignMessageHeader(message, foreignMessage);
                 } catch (Exception e) {
                    exception = e;
                    producer.sessionLogger.log(Level.INFO,
                         "Async send exceptioned: "+this.toString(true), e);
                    completed = false;
                 }
             }
             producer.session.asyncSendCBProcessor.wakeup();
         }
     }

     protected void processException(Exception ex) {
         synchronized(this) {
             if (completed || exception != null) {
                 return;
             }
             exception = ex;
         } 
         producer.sessionLogger.log(Level.INFO,
             "Async send exceptioned: "+this.toString(false), ex);
         producer.session.asyncSendCBProcessor.wakeup();
     }

     protected void callCompletionListener() {
         synchronized(this) {
             if (callbackCalled) {
                 return;
             }
             if (completed || exception != null) {
                 callbackCalled = true;
             } else if (isTimedout()) {
                 exception = timedoutEx; 
                 exception.fillInStackTrace();
                 callbackCalled = true;
             }
         }
         try {
         if (completed) {
             try {
                 completionListener.onCompletion(message);
             } catch (Exception ee) {
                 producer.sessionLogger.log(Level.WARNING, ee.getMessage()+this.toString(), ee);
             }
         } else if (exception != null) {
             try {
                 completionListener.onException(message, exception);
             } catch (Exception ee) {
                 producer.sessionLogger.log(Level.WARNING, ee.getMessage()+this.toString(), ee);
             }
         }

         } finally {
         producer.session.removeAsyncSendCallback(this);
         }
     }

     protected synchronized boolean isOnAckWait() {
         return onAckWait;
     }

     protected synchronized boolean isCompleted() {
         return completed;
     }

     protected synchronized boolean isExceptioned() {
         return (exception != null);
     }

     protected synchronized boolean isInTransaction() {
         return (transactionID != -1L);
     }

     private String toString(boolean getmid) {
         String str = "AsyncSendCallback[producer@" + producer.hashCode()+", "+destination;
         if (getmid) {
             try {
                 str = str+", "+message.getJMSMessageID();
             } catch (Exception e) {
                 str = str+", [message@"+message.hashCode()+":"+e.toString()+"]";
             }
         } else {
             str = str+", [message@"+message.hashCode()+"]";
         }
         str = str +", completed="+completed+", exception="+exception+
               ", sendSuccessReturn="+sendSuccessReturn+"]";
         return str;
     }

     public synchronized void dump (PrintStream ps) {
         ps.println ("------ AsyncSendCallback@"+this.hashCode()+" dump ------");
         ps.println("producer: @" + producer.hashCode());
         ps.println("destination: " + destination);
         if (completed) {
             try {
                 ps.println("message: " + message.getJMSMessageID());
             } catch (Exception e) {
                 ps.println("message: @" + message.hashCode()+":  "+e.toString()); 
             }
         } else {
             ps.println("message: @" + message.hashCode());
         }
         ps.println("completionListener: @" + completionListener.hashCode());
         if (foreignMessage == null) {
             ps.println("foreignMessage: null");
         } else {
             try {
                 ps.println("foreignMessage: @" +foreignMessage.getJMSMessageID());
             } catch (Exception e) {
                 ps.println("foreignMessage: @" +foreignMessage.hashCode()+": "+e.toString());
             }
         }
         ps.println("inTransaction: "+ !(transactionID == -1));
         ps.println("completed: " + completed);
         ps.println("exception: " + exception);
         ps.println("sendSuccessReturn: " + sendSuccessReturn);
         ps.println("sendReturned: " + sendReturned);
    }
}
