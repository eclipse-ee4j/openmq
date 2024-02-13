/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.util;

import com.sun.messaging.jmq.io.Status;

/**
 * this is the sub-class for exceptions thrown by the broker
 */

public class BrokerException extends Exception {
    private static final long serialVersionUID = -7016435578580516234L;

    /**
     * the "error-code" associated with the problem (if any)
     */
    private String errorID = null;

    /**
     * the status code associated with the problem (if any)
     */
    private int status = Status.ERROR;

    /**
     * Whether this exception's stacktrace has been logged
     */
    private boolean stackLogged = false;

    private boolean remote = false;
    private Object remoteBroker = null;
    private String remoteConsumers = "";
    private boolean sqlRecoverable = false;
    private boolean sqlReplayCheck = false;
    private boolean sqlReconnect = false;

    /**
     * create an exception with no message or root cause
     */
    public BrokerException() {
    }

    /**
     * create an exception with a message but no root cause
     *
     * @param msg the detail message
     */
    public BrokerException(String msg) {
        this(msg, null, null);
    }

    public BrokerException(String msg, int status) {
        this(msg, null, null, status);
    }

    public BrokerException(String msg, Throwable thr, int status) {
        this(msg, null, thr, status);
    }

    /**
     * create an exception with a message and a root cause
     *
     * @param msg the detail message
     * @param thr the root cause
     */
    public BrokerException(String msg, Throwable thr) {
        this(msg, null, thr);
    }

    /**
     * create an exception with a message but no root cause
     *
     * @param msg the detail message
     */
    public BrokerException(String msg, String errcode) {
        this(msg, errcode, (Throwable) null);
    }

    /**
     * create an exception with a message and a root cause
     *
     * @param msg the detail message
     * @param thr the root cause
     */
    public BrokerException(String msg, String errcode, Throwable thr) {
        this(msg, errcode, thr, Status.ERROR);
    }

    /**
     * create an exception with a message and a root cause
     *
     * @param msg the detail message
     * @param thr the root cause
     */
    public BrokerException(String msg, String errcode, Throwable thr, int status) {
        super(msg, thr);
        this.errorID = errcode;
        this.status = status;
    }

    /**
     * retrieves the error code associated with the exception
     *
     * @return the error code (if any)
     */
    public String getErrorCode() {
        return errorID;
    }

    /**
     * retrieves the status code associated with the exception
     *
     * @return the status code
     */
    public int getStatusCode() {
        return status;
    }

    public void overrideStatusCode(int s) {
        this.status = s;
    }

    public void setRemote(boolean v) {
        remote = v;
    }

    public boolean isRemote() {
        return remote;
    }

    public void setRemoteBrokerAddress(Object b) {
        remoteBroker = b;
    }

    public Object getRemoteBrokerAddress() {
        return remoteBroker;
    }

    public void setStackLogged() {
        stackLogged = true;
    }

    /**
     * To use this method, you are responsible to call setStackLogged()
     */
    public boolean isStackLogged() {
        return stackLogged;
    }

    /**
     * space separated ConsumerUID.longValue()s
     */
    public void setRemoteConsumerUIDs(String cuids) {
        remoteConsumers = cuids;
    }

    public String getRemoteConsumerUIDs() {
        return remoteConsumers;
    }

    public void setSQLRecoverable(boolean b) {
        sqlRecoverable = b;
    }

    public boolean getSQLRecoverable() {
        return sqlRecoverable;
    }

    public void setSQLReplayCheck(boolean b) {
        sqlReplayCheck = b;
    }

    public boolean getSQLReplayCheck() {
        return sqlReplayCheck;
    }

    public void setSQLReconnect(boolean v) {
        sqlReconnect = v;
    }

    public boolean getSQLReconnect() {
        return sqlReconnect;
    }

}
