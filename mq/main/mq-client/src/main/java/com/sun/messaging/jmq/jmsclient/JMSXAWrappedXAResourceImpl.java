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
 * @(#)JMSXAWrappedXAResourceImpl.java	1.8 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
import javax.transaction.xa.*;
import java.util.StringTokenizer;
import java.util.NoSuchElementException;
import com.sun.jms.spi.xa.*;
import com.sun.messaging.AdministeredObject;


/**
  * JMSXAWrappedXAResourceImpl 
  *
  * <p>The XAResource interface is a Java mapping of the industry standard
  * XA interface based on the X/Open CAE Specification (Distributed
  * Transaction Processing: The XA Specification).
  *
  * <p>The XA interface defines the contract between a Resource Manager
  * and a Transaction Manager in a distributed transaction processing
  * (DTP) environment. A JDBC driver or a JMS provider implements
  * this interface to support the association between a global transaction
  * and a database or message service connection.
  *
  * <p>The XAResource interface can be supported by any transactional
  * resource that is intended to be used by application programs in an
  * environment where transactions are controlled by an external
  * transaction manager. An example of such a resource is a database
  * management system. An application may access data through multiple
  * database connections. Each database connection is enlisted with
  * the transaction manager as a transactional resource. The transaction
  * manager obtains an XAResource for each connection participating
  * in a global transaction. The transaction manager uses the
  * <code>start</code> method
  * to associate the global transaction with the resource, and it uses the
  * <code>end</code> method to disassociate the transaction from
  * the resource. The resource
  * manager is responsible for associating the global transaction to all
  * work performed on its data between the start and end method invocations.
  *
  * <p>At transaction commit time, the resource managers are informed by
  * the transaction manager to prepare, commit, or rollback a transaction
  * according to the two-phase commit protocol.</p>
  *
  * @see javax.transaction.xa.XAResource
  */

public class JMSXAWrappedXAResourceImpl implements XAResource {

    private final static boolean debug = JMSXAWrappedConnectionFactoryImpl.debug;

    private XAResource xar;
    private JMSXAConnectionFactory cf = null;

    private boolean isQueue;
    private String username = null;
    private String password = null;

    private boolean closed = false;
    private JMSXAWrappedTransactionListener listener = null;

    public JMSXAWrappedXAResourceImpl(XAResource xar, 
                            boolean isQueue,
                            JMSXAConnectionFactory cf,
                            String username, String password) {
        this.xar = xar;
        this.cf = cf;
        this.isQueue = isQueue;
        this.username = username;
        this.password = password;
    }

    protected void setTransactionListener(JMSXAWrappedTransactionListener l) {
        listener = l;
    }

    protected void close() {
        closed = true;
    }

    /**
     * Commits the global transaction specified by xid.
     *
     * @param foreignXid A global transaction identifier
     *
     * @param onePhase If true, the resource manager should use a one-phase
     * commit protocol to commit the work done on behalf of xid.
     *
     * @exception XAException An error has occurred. Possible XAExceptions
     * are XA_HEURHAZ, XA_HEURCOM, XA_HEURRB, XA_HEURMIX, XAER_RMERR,
     * XAER_RMFAIL, XAER_NOTA, XAER_INVAL, or XAER_PROTO.
     *
     * <P>If the resource manager did not commit the transaction and the
     *  parameter onePhase is set to true, the resource manager may throw
	 *  one of the XA_RB* exceptions. Upon return, the resource manager has 
     *  rolled back the branch's work and has released all held resources.  
     */ 

	 public void commit(Xid foreignXid, boolean onePhase) throws XAException { 
         if (!closed || xar instanceof com.sun.messaging.jmq.jmsclient.XAResourceImpl) {
            dlog("commit(Xid="+foreignXid+" onePhase="+onePhase+"), closed="+closed); 

            boolean completed = false;
            try {
            if (listener != null) listener.beforeTransactionComplete();

            xar.commit(foreignXid, onePhase);
            completed = true;

            } catch (XAException e ) {

            switch (e.errorCode) {
            case XAException.XA_HEURHAZ:
            case XAException.XA_HEURCOM:
            case XAException.XA_HEURRB:
            case XAException.XA_HEURMIX:
            completed = false; /* wait forget */ 
            break;
            case XAException.XA_RETRY: completed = false; /* allowed only for 2-phase commit */
            break;
            case XAException.XA_RBROLLBACK:
            case XAException.XA_RBCOMMFAIL:
            case XAException.XA_RBDEADLOCK:
            case XAException.XA_RBINTEGRITY:
            case XAException.XA_RBOTHER:
            case XAException.XA_RBPROTO:
            case XAException.XA_RBTIMEOUT:
            case XAException.XA_RBTRANSIENT:
            completed = true; /* allowed only for 1-phase commit */ 
            break;
            case XAException.XAER_RMFAIL:
            case XAException.XAER_NOTA:     /* ?? */
            case XAException.XAER_INVAL:    /* ?? */
            case XAException.XAER_PROTO:
            default: completed = true; 
            }
            throw e;

            } finally {
            if (listener != null) listener.afterTransactionComplete(foreignXid, completed);
            }
        }
        else {
            JMSXAConnection c = null;
            try {
                dlog("closed commit() ...");
                c = getConnection();
                XAResource r = getXAResource(c);
				dlog("closed commit(Xid="+foreignXid+" onePhase="+onePhase+")");
                r.commit(foreignXid, onePhase);
            }
            catch (JMSException e) {
                log("Error:", e);
                throw new XAException(XAException.XAER_RMFAIL);
            }
            finally {
                if (c != null) {
                    try {
                        c.close();
                    }
                    catch (Exception e) {
					    log(AdministeredObject.cr.getKString(AdministeredObject.cr.W_WARNING),
                            "commit():Connection.close(): " + e.getMessage());
                    }
                }
            }
        }
    }


    /**
     * Ends the work performed on behalf of a transaction branch.
     * The resource manager disassociates the XA resource from the
     * transaction branch specified and lets the transaction
     * complete.
     *
     * <p>If TMSUSPEND is specified in the flags, the transaction branch
     * is temporarily suspended in an incomplete state. The transaction
     * context is in a suspended state and must be resumed via the
     * <code>start</code> method with TMRESUME specified.</p>
     *
     * <p>If TMFAIL is specified, the portion of work has failed.
     * The resource manager may mark the transaction as rollback-only</p>
     *
     * <p>If TMSUCCESS is specified, the portion of work has completed
     * successfully.</p>
     *
     * @param foreignXid A global transaction identifier that is the same as
     * the identifier used previously in the <code>start</code> method.
     *
     * @param flags One of TMSUCCESS, TMFAIL, or TMSUSPEND.
     *
     * @exception XAException An error has occurred. Possible XAException
     * values are XAER_RMERR, XAER_RMFAILED, XAER_NOTA, XAER_INVAL,
     * XAER_PROTO, or XA_RB*.
     */
    public void end(Xid foreignXid, int flags) throws XAException {
        boolean completed = false;
        try {
        if (listener != null) listener.beforeTransactionComplete();

        xar.end(foreignXid, flags); 
        } catch (XAException e) {

        switch(e.errorCode) {
        case XAException.XA_RBROLLBACK:
        case XAException.XA_RBCOMMFAIL:
        case XAException.XA_RBDEADLOCK:
        case XAException.XA_RBINTEGRITY:
        case XAException.XA_RBOTHER:
        case XAException.XA_RBPROTO:
        case XAException.XA_RBTIMEOUT:
        case XAException.XA_RBTRANSIENT:
        completed = false; 
        break;
        default: completed = true;
        }
        throw e;

        } finally {
        if (listener != null) listener.afterTransactionComplete(foreignXid, completed);
        }
    }

    /**
     * Tells the resource manager to forget about a heuristically
     * completed transaction branch.
     *
     * @param foreignXid A global transaction identifier.
     *
     * @exception XAException An error has occurred. Possible exception
     * values are XAER_RMERR, XAER_RMFAIL, XAER_NOTA, XAER_INVAL, or
     * XAER_PROTO.
     */
    public void forget(Xid foreignXid) throws XAException {
        if (!closed || xar instanceof com.sun.messaging.jmq.jmsclient.XAResourceImpl) {
            dlog("forget(Xid="+foreignXid+"), closed="+closed);
            boolean completed = false;
            try {
            if (listener != null) listener.beforeTransactionComplete();

            xar.forget(foreignXid);
            completed = true;

            } finally {
            if (listener != null) listener.afterTransactionComplete(foreignXid, completed);
            }
        }
        else {
            JMSXAConnection c = null;
            try {
                dlog("closed forget(Xid="+foreignXid+")");
                c = getConnection();
                XAResource r = getXAResource(c);
                r.forget(foreignXid);
            }
            catch (JMSException e) {
                log("Error:", e);
                throw new XAException(XAException.XAER_RMFAIL);
            }
            finally {
                if (c != null) {
                    try {
                        c.close();
                    }
                    catch (Exception e) {
					    log(AdministeredObject.cr.getKString(AdministeredObject.cr.W_WARNING),
                            "forget():Connection.close(): " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Obtains the current transaction timeout value set for this
     * XAResource instance. If <CODE>XAResource.setTransactionTimeout</CODE>
     * was not used prior to invoking this method, the return value
     * is the default timeout set for the resource manager; otherwise,
     * the value used in the previous <CODE>setTransactionTimeout</CODE>
     * call is returned.
     *
     * @return the transaction timeout value in seconds.
     *
     * @exception XAException An error has occurred. Possible exception
     * values are XAER_RMERR and XAER_RMFAIL.
     */
    public int getTransactionTimeout() throws XAException {
        return xar.getTransactionTimeout();
    }

    /**
     * This method is called to determine if the resource manager
     * instance represented by the target object is the same as the
     * resouce manager instance represented by the parameter <i>xares</i>.
     *
     * @param foreignXaRes An XAResource object whose resource manager instance
     *      is to be compared with the resource manager instance of the
     *      target object.
     *
     * @return <i>true</i> if it's the same RM instance; otherwise
     *       <i>false</i>.
     *
     * @exception XAException An error has occurred. Possible exception
     * values are XAER_RMERR and XAER_RMFAIL.
     *
     */
    public boolean isSameRM(XAResource foreignXaRes) throws XAException {
        dlog(xar.getClass().getName()+".isSameRM("+foreignXaRes.getClass().getName()+")");
        return xar.isSameRM(foreignXaRes);
    }


    /**
     * Ask the resource manager to prepare for a transaction commit
     * of the transaction specified in xid.
     *
     * @param foreignXid A global transaction identifier.
     *
     * @exception XAException An error has occurred. Possible exception
     * values are: XA_RB*, XAER_RMERR, XAER_RMFAIL, XAER_NOTA, XAER_INVAL,
     * or XAER_PROTO.
     *
     * @return A value indicating the resource manager's vote on the
     * outcome of the transaction. The possible values are: XA_RDONLY
     * or XA_OK. If the resource manager wants to roll back the
     * transaction, it should do so by raising an appropriate XAException
     * in the prepare method.
     */
    public int prepare(Xid foreignXid) throws XAException {
        if (!closed || xar instanceof com.sun.messaging.jmq.jmsclient.XAResourceImpl) {
            dlog("prepare(Xid="+foreignXid+"), closed="+closed);
            boolean completed = false;
            int ret = XA_OK;
            try {
            if (listener != null) listener.beforeTransactionComplete();

            ret = xar.prepare(foreignXid);
            if (ret == XA_RDONLY) completed = true;
            return ret;

            } catch (XAException e) {

            switch (e.errorCode) {
            case XAException.XA_RBROLLBACK:
            case XAException.XA_RBCOMMFAIL:
            case XAException.XA_RBDEADLOCK:
            case XAException.XA_RBINTEGRITY:
            case XAException.XA_RBOTHER:
            case XAException.XA_RBPROTO:
            case XAException.XA_RBTIMEOUT:
            case XAException.XA_RBTRANSIENT:
            completed = true;
            break;
            default: completed = true;
            }
            throw e;

            } finally {
            if (listener != null) listener.afterTransactionComplete(foreignXid, completed);
            }

        }
        else {
            JMSXAConnection c = null;
            try {
                dlog("closed prepare(Xid="+foreignXid+")");
                c = getConnection();
                XAResource r = getXAResource(c);
                return r.prepare(foreignXid);
            }
            catch (JMSException e) {
                log("Error:", e);
                throw new XAException(XAException.XAER_RMFAIL);
            }
            finally {
                if (c != null) {
                    try {
                        c.close();
                    }
                    catch (Exception e) {
					    log(AdministeredObject.cr.getKString(AdministeredObject.cr.W_WARNING),
                            "prepare():Connection.close():" + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Obtains a list of prepared transaction branches from a resource
     * manager. The transaction manager calls this method during recovery
     * to obtain the list of transaction branches that are currently in
     * prepared or heuristically completed states.
     *
     * @param flags One of TMSTARTRSCAN, TMENDRSCAN, TMNOFLAGS. TMNOFLAGS
     * must be used when no other flags are set in the parameter.
     *
     * @exception XAException An error has occurred. Possible values are
     * XAER_RMERR, XAER_RMFAIL, XAER_INVAL, and XAER_PROTO.
     *
     * @return The resource manager returns zero or more XIDs of the
     * transaction branches that are currently in a prepared or
     * heuristically completed state. If an error occurs during the
     * operation, the resource manager should throw the appropriate
     * XAException.
     *
     */
    public Xid[] recover(int flags) throws XAException {
        return xar.recover(flags);
    }

    /**
     * Informs the resource manager to roll back work done on behalf
     * of a transaction branch.
     *
     * @param foreignXid A global transaction identifier.
     *
     * @exception XAException An error has occurred.
     */
    public void rollback(Xid foreignXid) throws XAException {
        if (!closed || xar instanceof com.sun.messaging.jmq.jmsclient.XAResourceImpl) {
            dlog("rollback(Xid="+foreignXid+"), closed="+closed);
            boolean completed = false;
            try {
            if (listener != null) listener.beforeTransactionComplete();

            xar.rollback(foreignXid);
            completed = true;

            } catch (XAException e) {
  
            switch (e.errorCode) {
            case XAException.XA_HEURHAZ:
            case XAException.XA_HEURCOM:
            case XAException.XA_HEURRB:
            case XAException.XA_HEURMIX:
            completed = false; /* wait forget */ 
            break;
            case XAException.XA_RBROLLBACK:
            case XAException.XA_RBCOMMFAIL:
            case XAException.XA_RBDEADLOCK:
            case XAException.XA_RBINTEGRITY:
            case XAException.XA_RBOTHER:
            case XAException.XA_RBPROTO:
            case XAException.XA_RBTIMEOUT:
            case XAException.XA_RBTRANSIENT:
            completed = true;  /* typically for tran marked rollback-only */ 
            break;
            default: completed = true;
            }
            throw e;

            } finally {
            if (listener != null) listener.afterTransactionComplete(foreignXid, completed);
            }
        }
        else {
            JMSXAConnection c = null;
            try {
                dlog("closed rollback() ...");
                c = getConnection();
                XAResource r = getXAResource(c);
				dlog("closed rollback(Xid="+foreignXid+")");
                r.rollback(foreignXid);
            }
            catch (JMSException e) {
                log("Error:", e);
                throw new XAException(XAException.XAER_RMFAIL);
            }
            finally {
                if (c != null) {
                    try {
                        c.close();
                    }
                    catch (Exception e) {
					    log(AdministeredObject.cr.getKString(AdministeredObject.cr.W_WARNING),
                            "rollback():Connection.close():" + e.getMessage());
                    }
                }
            }
        }
    }


    /**
     * <P>Sets the current transaction timeout value for this <CODE>XAResource</CODE>
     * instance. Once set, this timeout value is effective until
     * <code>setTransactionTimeout</code> is invoked again with a different
     * value. To reset the timeout value to the default value used by the resource
     * manager, set the value to zero.
     *
     * If the timeout operation is performed successfully, the method returns
     * <i>true</i>; otherwise <i>false</i>. If a resource manager does not
     * support explicitly setting the transaction timeout value, this method
     * returns <i>false</i>.
     *
     * @param transactionTimeout The transaction timeout value in seconds.
     *
     * @return <i>true</i> if the transaction timeout value is set successfully;
     *       otherwise <i>false</i>.
     *
     * @exception XAException An error has occurred. Possible exception values
     * are XAER_RMERR, XAER_RMFAIL, or XAER_INVAL.
     */
    public boolean setTransactionTimeout(int transactionTimeout) throws XAException {
        return xar.setTransactionTimeout(transactionTimeout);
    }

    /**
     * Starts work on behalf of a transaction branch specified in
     * <code>foreignXid</code>.
     *
     * If TMJOIN is specified, the start applies to joining a transaction
     * previously seen by the resource manager. If TMRESUME is specified,
     * the start applies to resuming a suspended transaction specified in the
     * parameter <code>foreignXid</code>.
     *
     * If neither TMJOIN nor TMRESUME is specified and the transaction
     * specified by <code>foreignXid</code> has previously been seen by the resource
     * manager, the resource manager throws the XAException exception with
     * XAER_DUPID error code.
     *
     * @param foreignXid A global transaction identifier to be associated
     * with the resource.
     *
     * @param flags One of TMNOFLAGS, TMJOIN, or TMRESUME.
     *
     * @exception XAException An error has occurred. Possible exceptions
     * are XA_RB*, XAER_RMERR, XAER_RMFAIL, XAER_DUPID, XAER_OUTSIDE,
     * XAER_NOTA, XAER_INVAL, or XAER_PROTO.
     *
     */
    public void start(Xid foreignXid, int flags) throws XAException {
        boolean started = false;
        try {
        if (listener != null) listener.beforeTransactionStart();

        xar.start(foreignXid, flags);

        /* assume TMJOIN/TMRESUME has a corresponding TMNOFLAGS start
         * that is, they cause 1 transaction completion */

        if (flags == TMNOFLAGS) started = true; 
         
        } catch (JMSException e) {
        log("Error:", e);
        throw new XAException(XAException.XAER_RMFAIL); 
        } catch (XAException e) {

        switch(e.errorCode) {
        case XAException.XA_RBROLLBACK:
        case XAException.XA_RBCOMMFAIL:
        case XAException.XA_RBDEADLOCK:
        case XAException.XA_RBINTEGRITY:
        case XAException.XA_RBOTHER:
        case XAException.XA_RBPROTO:
        case XAException.XA_RBTIMEOUT:
        case XAException.XA_RBTRANSIENT:
        started = false;     /* ??? may cause negative 'transactions' count in Session */
        break;
        default: started = false;
        }
        throw e;

        } finally {
        if (listener != null) listener.afterTransactionStart(foreignXid, started);
        }
    }

    private JMSXAConnection getConnection() throws JMSException {
        JMSXAConnection c = null;
        if (isQueue) {
            if (username == null) {
                c = ((JMSXAQueueConnectionFactory)cf).createXAQueueConnection();
            }
            else {
                c = ((JMSXAQueueConnectionFactory)cf).createXAQueueConnection(username, password);
            }
        }
        else {
            if (username == null) {
                c = ((JMSXATopicConnectionFactory)cf).createXATopicConnection();
            }
            else {
                c = ((JMSXATopicConnectionFactory)cf).createXATopicConnection(username, password);
            }
        }
        return c;
    }

    public XAResource getDelegatedXAResource() {
        return xar;
    }
    
    private XAResource getXAResource(JMSXAConnection c) throws JMSException, XAException {
        JMSXASession s;
        if (isQueue) {
            s = ((JMSXAQueueConnection)c).createXAQueueSession(true, Session.AUTO_ACKNOWLEDGE);
        }
        else {
            s = ((JMSXATopicConnection)c).createXATopicSession(true, Session.AUTO_ACKNOWLEDGE);
        }
        XAResource r = s.getXAResource();

        String rc = null;
        if (r instanceof com.sun.messaging.jmq.jmsclient.JMSXAWrappedXAResourceImpl) {
            rc = ((com.sun.messaging.jmq.jmsclient.JMSXAWrappedXAResourceImpl)
                   r).getDelegatedXAResource().getClass().getName();
        } else {
            rc = r.getClass().getName();
        }
        boolean skip = isSystemPropertySetFor("skipIsSameRMCheckForExternalJMSXAResource", rc);

        if (r.isSameRM(xar)) {
            dlog("isSameRM() true - " + xar.getClass().getName());
            try {
                int t = xar.getTransactionTimeout();
                if (t >= 0) { 
                    r.setTransactionTimeout(t);
                }
            }
            catch (Exception e) {
                log(AdministeredObject.cr.getKString(AdministeredObject.cr.W_WARNING),
                    "get/setTransactionTimeout(): " +e.getMessage());  
            }
        }
        else {
            if (!skip) {
                log("Error:",  "isSameRM() false - "+xar.getClass().getName());
                throw new XAException(XAException.XAER_RMFAIL);
            } else {
                log(AdministeredObject.cr.getKString(AdministeredObject.cr.W_WARNING),
                    "isSameRM() false, ignore.  - "+xar.getClass().getName());
            }
        }
        return r;
    }

    public static boolean isSystemPropertySetFor(String propName, String propValue) {
        boolean isSet = false;
        String values = System.getProperty(propName);

        if (values == null) return false; 

        StringTokenizer token = new StringTokenizer(values, ",");
        try {
            while (token.hasMoreTokens()) {
                if (propValue.equals((token.nextToken().trim()))) {
                   isSet = true;
                   break;
                }
            }
        } catch (NoSuchElementException e) {}
        return isSet;
    }

    private final static void dlog(String msg) {
        if (debug) log("Info:", msg);
    }

    private final static void log(String level, Exception e) {
        log(level, e.getMessage());
        e.printStackTrace();
    }
    private final static void log(String level, String msg) {
        System.out.println(level+ " "+ "JMSXAWrappedXAResource: " + msg);
    }

}

/****************

@(#)seebeyond.txt	1.9 04/10/17


SeeBeyond's Issue: Session close causing RollbackException 
                   on commit in MDB CMT


According to SeeBeyond, in its XASession implementation, session
close will cause not only local transactions to rollback but also 
global (XA) transactions to rollback.

SeeBeyond claimed that to change their implementation is too risky and 
is not something they want to consider.  To support Sun customer Syntegra,
we are planning to provide a workaround in our wrapper classes for AS7 foreign
JMS provider to better work with SeeBeyond's JMS implementation.

The workaround is to delay session close until a started XA transaction
completes.

There are possibly 3 approaches to address the problem:


1. Code changes in MQ source in JMS wrapper classes for foreign JMS providers
   cons: no access to Transaction/TransactionManager objects therefore 
         can't get transaction completion notification using 
         javax.transaction.Synchronization.

   pros: only need to patch MQ jar file
         affect only foreign JMS provider support in AS7

2. Code changes in AS7 source in JMS wrapper classes
   cons: affects MQ integration source in AS7
         would need patch AS7

3. Code changes in AS7 source and MQ source
   pros: scope the change in AS7 source as small as possible

   cons: would need patch both AS7 and MQ jar file


Since we are planning to provide the workaround for only one customer,
we are planning to use #1 approach with some limitations.

Notes on Modifications on MQ's JMS Wrapper Classes for Foreign JMS Providers
---------------------------------------------------------------------------
1. Transaction completion detection 
   . Use XAResource.commit/rollback (including forget call) calls to detect 
     transaction completion

   . XAException error codes will also be checked for XAResource.commit/rollback
     calls to distinguish those XAExceptions that indicates transaction completion
     and those indicates transaction not completed* 
     

   limitation: Since we can't use javax.transaction.Synchronization, our transaction
               completion detection through XAResource object would not be bullet-proof. 
               There can be edge cases where a transaction completion is not detected 
               which would cause Session leak or where a Session can be closed too early
               due to vagueness in the XA/JTA specs for some edge cases. But it's our 
               understanding that these edge cases should not be common.  Understanding
               SeeBeyond's XA resource manager implementation detail maybe able to help
               narrow down some of these edge cases.  

               This transaction completion detection also has the assumption that a 
               transaction started on a XAResource object will always be completed
               on the same XAResource object.  We expect SeeBeyond's XAResource 
               implementation meets this requirement since its XAResource.isSameRM
               always returns false for two different XAResource objects.

               Since Connection will not be hard-closed until all its Sessions have
               been hard-closed (see #3), session leak would also lead to connection
               leak.  Therefore applications should provide some connection monitoring/
               cleanup mechanism to periodically monitor/cleanup leaked connections
               if Connection leak does become an issue.

            *: see more details at XAResource XAException Checks below


2. JMS Session close semantic 
   . Session close is deferred until transaction completes

   . Session.close is NOT deferred if there is a Session MessageListener

   . Operations on 'soft-closed' Session will NOT be prevented


   limitations: 
   . no JMS Session close semantic of throwing IllegalStateException when
     using the closed Session or its producers/consumers  
   . no JMS Session close semantic of unblocking MessageConsumer.receive
   . no JMS Session close semantic of waiting MessageConsumer.receive 
     orderly returning the message 
   . no JMS Session close semantic of waiting for all MessageConsumer's 
        MessageListeners return


3. JMS Connection close semantic
   . Connection close is deferred until all sessions has been hard-closed

   . Operations (except create XASession) on 'soft-closed' Connection will NOT be prevented

   . See 'JMS Session close semantic'

   limitation:
   . no JMS Connection close semantic of throwing IllegalStateException when 
     using a closed Connection (except create XASession) or its Sessions
   . no JMS Connection close semantic of only return until message processing
     has been orderly shut down 


4. The Session.close()/Connection.close deferral is turned on by setting following
   jvm system property 

   delaySessionCloseForExternalJMSXAResource

   which is a ',' separated list of fully-qualified class name strings
   that are XAResource implementation classes for an external JMS provider.


XAResource XAException Checks
-----------------------------
A XASession will only do soft-close if there is any uncompleted XA transaction
that has been started on its XAResource (and there is no Session MessageListener
set for the XASession).  A soft-closed XASession will be hard-closed only when all
XA transactions that have started on its XAResource have been completed or if its 
Connection is closed.

1. XAResource.commit

  *The transaction will not mark as completed for following XAExceptions:
   (expecting XAResource.forget to be called later
    XA spec:  A resource manager that heuristically completes work done
              on behalf of a transaction branch must keep track of that
              branch along with the decision (that is, heuristically
              committed, rolled back, or mixed)
   XA_HEURHAZ
   XA_HEURCOM
   XA_HEURRB
   XA_HEURMIX

   XA_RETRY
   (XA spec: All resources held on behalf of *xid remain in a prepared
             state until commitment is possible.) 


  *The transaction will be marked as completed for following XAExceptions:
   (XA spec: Upon return the resource manager has rolled back the
             branch's work and has released all held resources.
             Only applicable to 1-phase commit)
   XA_RBROLLBACK
   XA_RBCOMMFAIL
   XA_RBDEADLOCK
   XA_RBINTEGRITY
   XA_RBOTHER
   XA_RBPROTO
   XA_RBTIMEOUT
   XA_RBTRANSIENT

  *All other XAExceptions will mark the transaction completion 
   (XA spec: not clear)


2. XAResource.rollback

  *The transaction will not mark as completed for following XAExceptions:
   (expecting XAResource.forget to be called later
    XA spec:  A resource manager that heuristically completes work done
              on behalf of a transaction branch must keep track of that
              branch along with the decision (that is, heuristically
              committed, rolled back, or mixed)
   XA_HEURHAZ
   XA_HEURCOM
   XA_HEURRB
   XA_HEURMIX
   

  *The transaction will be marked as completed for following XAExceptions:   
   (XA spec: The resource manager has rolled back the transaction branch's
             work and has released all held resources.
             typically occurs when the branch was already marked rollback-only)
         
   XA_RBROLLBACK
   XA_RBCOMMFAIL
   XA_RBDEADLOCK
   XA_RBINTEGRITY
   XA_RBOTHER
   XA_RBPROTO
   XA_RBTIMEOUT
   XA_RBTRANSIENT

  *All other XAExceptions will mark the transaction completion 
   (CA spec: not clear)


3. XAResource.forget

   The transaction is marked as completed for any XAExceptions
   (XA spec: not clear)


4. XAResource.start
  *A new transaction is not marked as started for XAExceptions:
   (XA spec: The resource manager has not associated the transaction
             branch with the thread of control and has marked *xid
             rollback-only.) 
             
   XA_RBROLLBACK
   XA_RBCOMMFAIL
   XA_RBDEADLOCK
   XA_RBINTEGRITY
   XA_RBOTHER
   XA_RBPROTO
   XA_RBTIMEOUT
   XA_RBTRANSIENT

  *A transaction is not marked as started for any other XAExceptions

  *If TMJOIN or TMRESUME flag set, it will not mark a new transaction
   as started regardless it throws XAException or not


5. XAResource.end

  *The transaction will not be marked as completed for following XAExceptions:
   (expecting rollback to be called
    XA spec: The resource manager has dissociated the transaction branch
             from the thread of control and has marked rollback-only the
             work performed on behalf of *xid.)
 
   XA_RBROLLBACK
   XA_RBCOMMFAIL
   XA_RBDEADLOCK
   XA_RBINTEGRITY
   XA_RBOTHER
   XA_RBPROTO
   XA_RBTIMEOUT
   XA_RBTRANSIENT
   
  *The transaction will be marked as completed for any other XAExceptions
   (XA spec: not clear)

  *The transaction is not marked as completed when no XAException

5. XAResource.prepare

  * The transaction will be marked as completed if returns XA_RDONLY 
    (XA spec: XA_RDONLY - The resource manager may release all resources 
                          and forget about the branch. committed)

  * The transaction will not be marked as completed if returns XA_OK
    (XA spec: A resource manager cannot erase its knowledge of a branch
              until the transaction manager calls either commit or rollback) 

  * The transaction will be marked as completed for following XAException:
    (XA spec: - Upon return, the resource manager has rolled back the branch's
                work and has released all held resources.)

    XA_RBROLLBACK
    XA_RBCOMMFAIL
    XA_RBDEADLOCK
    XA_RBINTEGRITY
    XA_RBOTHER
    XA_RBPROTO
    XA_RBTIMEOUT
    XA_RBTRANSIENT

  *  The transaction will be marked as completed for any other XAException
*****************/
