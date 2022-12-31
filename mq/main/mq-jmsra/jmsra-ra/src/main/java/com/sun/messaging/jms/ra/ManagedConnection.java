/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

import java.io.PrintWriter;
import java.util.logging.Logger;

import javax.security.auth.Subject;

import jakarta.jms.JMSException;
import jakarta.jms.InvalidClientIDException;

import jakarta.resource.*;
import jakarta.resource.spi.*;

import com.sun.messaging.jms.ra.ConnectionRequestInfo.ConnectionType;
import com.sun.messaging.jms.ra.api.JMSRAManagedConnection;
import com.sun.messaging.jmq.jmsclient.XAConnectionImpl;

/**
 * Implements the ManagedConnection interface of the Java EE Connector Architecture.
 */

public class ManagedConnection implements jakarta.resource.spi.ManagedConnection, JMSRAManagedConnection {
    /** The ManagedConnectionFactory instance associated with this ManagedConnection */
    private com.sun.messaging.jms.ra.ManagedConnectionFactory mcf = null;

    /** The ConnectionAdapter for the XAConnection */
    private ConnectionAdapter ca = null;

    /** The XAResource for this ManagedConnection */
    private com.sun.messaging.jmq.jmsclient.XAResourceForMC xar = null;
    private DirectXAResource dxar = null;

    /** The XAConnection for this ManagedConnection */
    private com.sun.messaging.jmq.jmsclient.XAConnectionImpl xac = null;
    private DirectConnection dc = null;

    /** The Connection Event Listener for this ManagedConnection */
    private com.sun.messaging.jms.ra.ConnectionEventListener evtlistener = null;

    /** The LocalTransaction for this ManagedConnection */
    private com.sun.messaging.jms.ra.LocalTransaction localTransaction = null;
    private com.sun.messaging.jms.ra.DirectLocalTransaction directLocalTransaction = null;
    /** Flag to indicate whether the LocalTransaction is active */
    private boolean ltActive = false;

    /** The ManagedConnectionMetaData for this ManagedConnection */
    private com.sun.messaging.jms.ra.ManagedConnectionMetaData mcMetaData = null;

    /** The Password Credential for this ManagedConnection */
    private jakarta.resource.spi.security.PasswordCredential pwCredential = null;

    // whether this connection uses old-style direct mode implemented in the RA
    private boolean isRADirect = false;

    /** Flag to indicate whether this ManagedConnection has been destroyed */
    private boolean destroyed = false;

    /** The PrintWriter set on this ManagedConnectionFactory */
    private PrintWriter logWriter = null;

    /** The identifier (unique in a VM) for this ManagedConnection */
    private int mcId = 0;

    /** The uniquifier */
    private static int idCounter = 0;

    /* Loggers */
    private static final String _className = "com.sun.messaging.jms.ra.ManagedConnectionFactory";
    protected static final String _lgrNameOutboundConnection = "javax.resourceadapter.mqjmsra.outbound.connection";
    protected static final Logger _loggerOC = Logger.getLogger(_lgrNameOutboundConnection);
    protected static final String _lgrMIDPrefix = "MQJMSRA_MC";
    protected static final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
    protected static final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
    protected static final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
    protected static final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
    protected static final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";

    public ManagedConnection(com.sun.messaging.jms.ra.ManagedConnectionFactory mcf, Subject subject,
            com.sun.messaging.jms.ra.ConnectionRequestInfo cxRequestInfo, com.sun.messaging.jms.ra.ResourceAdapter ra) throws ResourceException {
        String un, pw;

        _loggerOC.entering(_className, "constructor()");

        // Each instance gets its own Id
        mcId = ++idCounter;

        this.mcf = mcf;
        this.isRADirect = mcf.getEnableRADirect();
        pwCredential = Util.getPasswordCredential(mcf, subject, cxRequestInfo);
        var pwcValid = Util.isPasswordCredentialValid(subject);
        if (pwCredential != null) {
            if (pwcValid) {
                // CONT AUTH case - app must not use createConnection(u, p);
                if ((cxRequestInfo != null) && (cxRequestInfo.getUserName() != null)) {
                    _loggerOC.fine(_lgrMID_WRN + "createConnection API used w/ username, password for Container Auth");
                }
            } else {
                // APP AUTH case - app must use createConnection(u, p);
                if ((cxRequestInfo != null) && (cxRequestInfo.getUserName() == null)) {
                    _loggerOC.fine(_lgrMID_WRN + "createConnection API used w/o username, password for Application Auth");
                }
            }
            un = pwCredential.getUserName();
            pw = new String(pwCredential.getPassword());
            _loggerOC.finer(_lgrMID_INF + "constructor:Using pwCred:u,p=" + un);
        } else {
            un = mcf.getUserName();
            pw = mcf.getPassword();
            _loggerOC.finer(_lgrMID_INF + "constructor:Using mcfConfig:u,p=" + un);
        }
        try {
            _loggerOC.finer(_lgrMID_INF + "constructor:Creating mcId=" + mcId + ":Using xacf config=" + mcf._getXACF().getCurrentConfiguration());

            ConnectionCreator cc = mcf.getConnectionCreator();
            if (this.isRADirect) {
                if (cxRequestInfo != null && cxRequestInfo.getConnectionType() == ConnectionType.QUEUE_CONNECTION) {
                    this.dc = (DirectConnection) cc._createQueueConnection(un, pw);
                } else if (cxRequestInfo != null && cxRequestInfo.getConnectionType() == ConnectionType.TOPIC_CONNECTION) {
                    this.dc = (DirectConnection) cc._createTopicConnection(un, pw);
                } else {
                    this.dc = (DirectConnection) cc._createConnection(un, pw);
                }
                this.dc.setManaged(true, this);
            } else {
                xac = (XAConnectionImpl) (mcf._getXACF()).createXAConnection(un, pw);
            }

        } catch (jakarta.jms.JMSSecurityException jmsse) {
            jakarta.resource.spi.SecurityException se = new jakarta.resource.spi.SecurityException(
                    _lgrMID_EXC + "constructor:Aborting:JMSException on createConnection=" + jmsse.getMessage(), jmsse.getErrorCode());
            se.initCause(jmsse);
            _loggerOC.severe(se.getMessage());
            jmsse.printStackTrace();
            _loggerOC.throwing(_className, "constructor()", se);
            throw se;
        } catch (JMSException jmse) {
            jakarta.resource.ResourceException re = new jakarta.resource.ResourceException(
                    _lgrMID_EXC + "constructor:Aborting:JMSException on createConnection=" + jmse.getMessage(), jmse.getErrorCode());
            re.initCause(jmse);
            _loggerOC.severe(re.getMessage());
            jmse.printStackTrace();
            _loggerOC.throwing(_className, "constructor()", re);
            throw re;
        }

        evtlistener = new com.sun.messaging.jms.ra.ConnectionEventListener(this);

        try {
            if (this.isRADirect) {
                this.dc._setExceptionListener(evtlistener);
                this.directLocalTransaction = new com.sun.messaging.jms.ra.DirectLocalTransaction(this, dc);
            } else {
                xac._setExceptionListenerFromRA(evtlistener);
                xac.setExtendedEventNotification(true);
                if (cxRequestInfo != null && cxRequestInfo.getConnectionType() == ConnectionType.QUEUE_CONNECTION) {
                    ca = new QueueConnectionAdapter(this, xac, ra);
                } else if (cxRequestInfo != null && cxRequestInfo.getConnectionType() == ConnectionType.TOPIC_CONNECTION) {
                    ca = new TopicConnectionAdapter(this, xac, ra);
                } else {
                    ca = new ConnectionAdapter(this, xac, ra);
                }
                mcMetaData = new com.sun.messaging.jms.ra.ManagedConnectionMetaData(this);
                localTransaction = new com.sun.messaging.jms.ra.LocalTransaction(this, xac);
                _loggerOC.fine(_lgrMID_INF + "constructor:Created mcId=" + mcId + ":xacId=" + xac._getConnectionID() + ":Using xacf config="
                        + mcf._getXACF().getCurrentConfiguration());
                xac.setEventListener(evtlistener);
            }
        } catch (JMSException jmse) {
            throw new ResourceAdapterInternalException("MQRA:MC:JMSException upon setExceptionListener", jmse);
        }

        try {
            if (this.isRADirect) {
                this.dxar = this.dc._getXAResource();
            } else {
                xar = new com.sun.messaging.jmq.jmsclient.XAResourceForMC(this, xac, xac);
            }
        } catch (JMSException jmse) {
            String errMsg = "MQRA:MC:Constr:Exception on xar creation-" + jmse.getMessage();
            System.err.println(errMsg);
            jmse.printStackTrace();
            throw new ResourceAdapterInternalException(errMsg, jmse);
        }
        logWriter = null;
        _loggerOC.exiting(_className, "constructor()");
    }

    // ManagedConnection interface methods //
    //

    /**
     * Adds a ConnectionEventListener to this ManagedConnection
     *
     * @param listener The ConnectionEventListener to be added
     */
    @Override
    public void addConnectionEventListener(jakarta.resource.spi.ConnectionEventListener listener) {
        _loggerOC.entering(_className, "addConnectionEventListener():mcId=" + mcId, listener);
        evtlistener.addConnectionEventListener(listener);
    }

    /**
     * Removes a ConnectionEventListener from this ManagedConnection
     *
     * @param listener The ConnectionEventListener to be removed
     */
    @Override
    public void removeConnectionEventListener(jakarta.resource.spi.ConnectionEventListener listener) {
        _loggerOC.entering(_className, "removeConnectionEventListener():mcId=" + mcId, listener);
        evtlistener.removeConnectionEventListener(listener);
    }

    /**
     * Forces this ManagedConnection to cleanup any client maintained state that it holds. Any subsequent attempt by an
     * application component to use this connection after this must result in an Exception.
     */
    @Override
    public void cleanup() throws ResourceException {
        _loggerOC.entering(_className, "cleanup():mcId=" + mcId);
        checkDestroyed();
        if (this.isRADirect) {
            try {
                this.dc._cleanup();
            } catch (JMSException ex) {
                throw new ResourceException(ex);
            }
        } else {
            // Close the sessions on the ca for this mc
            if (ca != null) {
                ca.cleanup();
            }
        }
    }

    /**
     * Destroys this ManagedConnection and any client maintained state that it holds. Any subsequent attempt by an
     * application component to use this connection after this must result in an Exception.
     */
    @Override
    public void destroy() throws ResourceException {
        if (destroyed) {
            _loggerOC.warning(_lgrMID_WRN + "destroy:Previously destroyed-mcId=" + mcId);
        } else {
            if (this.isRADirect) {
                try {
                    this.dc.closeAndDestroy();
                } catch (JMSException ex) {
                    throw new ResourceException(ex);
                }
            } else {
                // Close the physical connection
                if (ca != null) {
                    ca.destroy();
                }
            }
            destroyed = true;
        }
    }

    /**
     * Returns the XAResource instance for this ManagedConnection instance
     *
     * @return A javax.transaction.xa.XAResource instance
     */
    @Override
    public javax.transaction.xa.XAResource getXAResource() throws ResourceException {
        _loggerOC.entering(_className, "getXAResource():mcId=" + mcId);
        checkDestroyed();
        if (this.isRADirect) {
            return dxar;
        } else {
            return xar;
        }
    }

    /**
     * Returns the LocalTransaction instance for this ManagedConnection instance
     *
     * @return A jakarta.resource.spi.LocalTransaction instance
     */
    @Override
    public jakarta.resource.spi.LocalTransaction getLocalTransaction() throws ResourceException {
        _loggerOC.entering(_className, "getLocalTransaction():mcId=" + mcId);
        checkDestroyed();
        if (this.isRADirect) {
            return this.directLocalTransaction;
        } else {
            return localTransaction;
        }
    }

    /**
     * Returns the ManagedConnectionMetaData instance for this ManagedConnection instance
     *
     * @return A jakarta.resource.spi.ManagedConnectionMetaData instance
     */
    @Override
    public jakarta.resource.spi.ManagedConnectionMetaData getMetaData() throws ResourceException {
        _loggerOC.entering(_className, "getMetaData():mcId=" + mcId);
        checkDestroyed();
        return mcMetaData;
    }

    /**
     * Returns a new connection handle. A ConnectionAdapter is returned. ConnectionMetaData informs that MaxConnections is
     * 1. Hence this is called only once per ManagedConnection instance.
     *
     * @param subject The javax.security.auth.Subject that is to be used for credentials
     * @param cxRequestInfo The ConnectionRequestInfo that is to be used for connection matching
     *
     * @return A JMS SessionAdapter instance
     */
    @Override
    public java.lang.Object getConnection(Subject subject, jakarta.resource.spi.ConnectionRequestInfo cxRequestInfo) throws ResourceException {

        jakarta.resource.spi.security.PasswordCredential pwCred;
        com.sun.messaging.jms.ra.ConnectionRequestInfo cri = (com.sun.messaging.jms.ra.ConnectionRequestInfo) cxRequestInfo;

        checkDestroyed();

        pwCred = Util.getPasswordCredential(mcf, subject, cri);

        if (!Util.isPasswordCredentialEqual(pwCred, pwCredential)) {
            throw new jakarta.resource.spi.SecurityException(
                    "MQRA:MC:getConnection-auth failed for Subject-" + ((subject != null) ? subject.toString() : "null-subject"));
        }
        String cid = mcf.getClientId();
        if (cid != null) {
            try {
                if (this.isRADirect) {
                    _loggerOC
                            .fine(_lgrMID_INF + "getConnection():mcId=" + mcId + ":xacId=" + this.dc.getConnectionId() + ":opening CA;setting ClientId:" + cid);
                    this.dc._activate(cid);
                } else {
                    _loggerOC.fine(_lgrMID_INF + "getConnection():mcId=" + mcId + ":xacId=" + xac._getConnectionID() + ":opening CA;setting ClientId:" + cid);
                    ca.open(cid);
                }
            } catch (InvalidClientIDException icide) {
                ResourceException re = new EISSystemException("MQRA:MC:InvalidClientIDException-" + icide.getMessage());
                re.initCause(icide);
                throw re;
            } catch (JMSException jmse) {
                ResourceException re = new EISSystemException("MQRA:MC:JMSException-" + jmse.getMessage());
                re.initCause(jmse);
                throw re;
            }
        } else {
            if (this.isRADirect) {
                try {
                    this.dc._activate(null);

                } catch (InvalidClientIDException icide) {
                    ResourceException re = new EISSystemException("MQRA:MC:InvalidClientIDException-" + icide.getMessage());
                    re.initCause(icide);
                    throw re;
                } catch (JMSException jmse) {
                    ResourceException re = new EISSystemException("MQRA:MC:JMSException-" + jmse.getMessage());
                    re.initCause(jmse);
                    throw re;
                }
            } else {
                _loggerOC.fine(_lgrMID_INF + "getConnection():mcId=" + mcId + ":xacId=" + xac._getConnectionID() + ":opening CA;NO ClientId");
                ca.open();
            }
        }
        if (this.isRADirect) {
            return this.dc;
        } else {
            return ca;
        }
    }

    /**
     * Associates an application-level connection handle with this ManagedConnection instance.
     *
     * @param connection The connection to associate
     *
     */
    @Override
    public void associateConnection(java.lang.Object connection) throws ResourceException {
        _loggerOC.entering(_className, "associateConnection():mcId=" + mcId, connection);

        checkDestroyed();

        if (this.isRADirect) {
            return;
        }
        // Check that 'connection' is our ConnectionAdapter
        if (connection instanceof ConnectionAdapter) {
            ConnectionAdapter connection_adapter = (ConnectionAdapter) connection;
            connection_adapter.associateManagedConnection(this);
            this.ca = connection_adapter;

        } else {
            throw new ResourceException("MQRA:MC:associateConnection-invalid connection:class=" + connection.getClass() + ":toString=" + connection.toString());
        }
    }

    /**
     * Sets the PrintWriter to be used by the ResourceAdapter for logging
     *
     * @param out The PrintWriter to be used
     */
    @Override
    public void setLogWriter(PrintWriter out) throws ResourceException {
        _loggerOC.entering(_className, "setLogWriter():mcId=" + mcId, out);
        logWriter = out;
    }

    /**
     * Returns the PrintWriter being used by the ResourceAdapter for logging
     *
     * @return The PrintWriter being used
     */
    @Override
    public PrintWriter getLogWriter() throws ResourceException {
        _loggerOC.entering(_className, "getLogWriter():mcId=" + mcId, logWriter);
        return logWriter;
    }

    @Override
    public int getMCId() {
        return mcId;
    }

    public com.sun.messaging.jms.ra.ManagedConnectionFactory getManagedConnectionFactory() {
        return mcf;
    }

    public jakarta.resource.spi.security.PasswordCredential getPasswordCredential() {
        return pwCredential;
    }

    @Override
    public ConnectionAdapter getConnectionAdapter() {
        return ca;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    @Override
    public boolean xaTransactionStarted() {
        if (ltActive) {
            if (this.isRADirect) {
                return this.directLocalTransaction.started();
            } else {
                return localTransaction.started();
            }
        } else {
            if (this.isRADirect) {
                return this.dxar.isEnlisted();
            } else {
                return xar.started();
            }
        }
    }

    public boolean xaTransactionActive() {
        if (ltActive) {
            if (this.isRADirect) {
                return this.directLocalTransaction.isActive();
            } else {
                return localTransaction.isActive();
            }
        } else {
            if (this.isRADirect) {
                return this.dxar.isEnlisted();
            } else {
                return xar.isActive();
            }
        }
    }

    @Override
    public long getTransactionID() {
        if (ltActive) {
            if (this.isRADirect) {
                return this.directLocalTransaction.getTransactionID();
            } else {
                return localTransaction.getTransactionID();
            }
        } else {
            if (this.isRADirect) {
                return this.dxar._getTransactionId();
            } else {
                return xar.getTransactionID();
            }
        }
    }

    public void setLTActive(boolean active) {
        ltActive = active;
    }

    public void sendEvent(int type, Exception ex) {
        evtlistener.sendEvent(type, ex, null);
    }

    public void sendEvent(int type, Exception ex, Object handle) {
        evtlistener.sendEvent(type, ex, handle);
    }

    protected void _setDirect(boolean value) {
        this.isRADirect = value;
    }

    /**
     * Checks if this ManagedConnection has been destoyed and throws an IllegalStateException if it has
     */
    private void checkDestroyed() throws ResourceException {
        if (destroyed) {
            throw new jakarta.resource.spi.IllegalStateException("MQRA:MC:Destroyed-Id=" + mcId);
        }
    }

}
