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

package com.sun.messaging.bridge.service.jms;

import javax.transaction.xa.*;

/* 
 * A XAResource wrapper - used in case of external transaction manager
 *
 * @author amyk
 *
 */

public class XAResourceImpl implements XAResource
{
    private XAResource _xar = null;

    public XAResourceImpl(XAResource xar) {
        _xar = xar;
    }

    /**	Commits the global transaction specified by xid.
      *
      * @param xid A global transaction identifier
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

    public void commit(Xid xid, boolean onePhase) throws XAException {
        _xar.commit(xid, onePhase);
    }


    /** Ends the work performed on behalf of a transaction branch.
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
      * @param xid A global transaction identifier that is the same as
      * the identifier used previously in the <code>start</code> method.
      *
      * @param flags One of TMSUCCESS, TMFAIL, or TMSUSPEND.
      *
      * @exception XAException An error has occurred. Possible XAException
      * values are XAER_RMERR, XAER_RMFAIL, XAER_NOTA, XAER_INVAL,
      * XAER_PROTO, or XA_RB*.
      */

    public void end(Xid xid, int flags) throws XAException {
        _xar.end(xid, flags);
    }


    /** Tells the resource manager to forget about a heuristically
      * completed transaction branch.
      *
      * @param xid A global transaction identifier.
      *
      * @exception XAException An error has occurred. Possible exception
      * values are XAER_RMERR, XAER_RMFAIL, XAER_NOTA, XAER_INVAL, or
      * XAER_PROTO.
      */

    public void forget(Xid xid) throws XAException {
        _xar.forget(xid);
    }

    /** Obtains the current transaction timeout value set for this
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
        return _xar.getTransactionTimeout();
    }

    /** This method is called to determine if the resource manager
      * instance represented by the target object is the same as the
      * resouce manager instance represented by the parameter <i>xares</i>.
      *
      * @param xares An XAResource object whose resource manager instance
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
    public boolean isSameRM(XAResource xares) throws XAException {
        if (xares instanceof XAResourceImpl) {
            return _xar.isSameRM(((XAResourceImpl)xares)._xar);
        } else {
            return _xar.isSameRM(xares);
        }
    }

    /** Ask the resource manager to prepare for a transaction commit
      * of the transaction specified in xid.
      *
      * @param xid A global transaction identifier.
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

    public int prepare(Xid xid) throws XAException {
        return _xar.prepare(xid);
    }


    /** Obtains a list of prepared transaction branches from a resource
      * manager. The transaction manager calls this method during recovery
      * to obtain the list of transaction branches that are currently in
      * prepared or heuristically completed states.
      *
      * @param flag One of TMSTARTRSCAN, TMENDRSCAN, TMNOFLAGS. TMNOFLAGS
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

    public Xid[] recover(int flag) throws XAException {
        return _xar.recover(flag);
    }


    /** Informs the resource manager to roll back work done on behalf
      * of a transaction branch.
      *
      * @param xid A global transaction identifier.
      *
      * @exception XAException An error has occurred. Possible XAExceptions are
      * XA_HEURHAZ, XA_HEURCOM, XA_HEURRB, XA_HEURMIX, XAER_RMERR, XAER_RMFAIL,
      * XAER_NOTA, XAER_INVAL, or XAER_PROTO.
      *
      * <p>If the transaction branch is already marked rollback-only the
      * resource manager may throw one of the XA_RB* exceptions. Upon return,
      * the resource manager has rolled back the branch's work and has released
      * all held resources.
      */

    public void rollback(Xid xid) throws XAException {
        _xar.rollback(xid);
    }


    /** <P>Sets the current transaction timeout value for this <CODE>XAResource</CODE>
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
      * @param seconds The transaction timeout value in seconds.
      *
      * @return <i>true</i> if the transaction timeout value is set successfully;
      *       otherwise <i>false</i>.
      *
      * @exception XAException An error has occurred. Possible exception values
      * are XAER_RMERR, XAER_RMFAIL, or XAER_INVAL.
      */
    public boolean setTransactionTimeout(int seconds) throws XAException {
        return _xar.setTransactionTimeout(seconds);
    }


    /** Starts work on behalf of a transaction branch specified in
      * <code>xid</code>.
      *
      * If TMJOIN is specified, the start applies to joining a transaction
      * previously seen by the resource manager. If TMRESUME is specified,
      * the start applies to resuming a suspended transaction specified in the
      * parameter <code>xid</code>.
      *
      * If neither TMJOIN nor TMRESUME is specified and the transaction
      * specified by <code>xid</code> has previously been seen by the resource
      * manager, the resource manager throws the XAException exception with
      * XAER_DUPID error code.
      *
      * @param xid A global transaction identifier to be associated
      * with the resource.
      *
      * @param flags One of TMNOFLAGS, TMJOIN, or TMRESUME.
      *
      * @exception XAException An error has occurred. Possible exceptions
      * are XA_RB*, XAER_RMERR, XAER_RMFAIL, XAER_DUPID, XAER_OUTSIDE,
      * XAER_NOTA, XAER_INVAL, or XAER_PROTO.
      *
      */
    public void start(Xid xid, int flags) throws XAException {
        _xar.start(xid, flags);
    }

}
