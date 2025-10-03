/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.util.Properties;
import jakarta.jms.JMSException;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.XAConnection;
import jakarta.jms.XAConnectionFactory;
import jakarta.jms.XAJMSContext;

import com.sun.messaging.bridge.api.BridgeContext;
import com.sun.messaging.jms.MQRuntimeException;

/**
 * @author amyk
 *
 */
public class XAConnectionFactoryImpl implements XAConnectionFactory, Refable {

    private XAConnectionFactory _cf = null;
    private String _ref = null;
    private boolean _isEmbeded = false;
    private boolean _isMultiRM = false;
    private boolean _firstTime = true;

    private BridgeContext _bc = null;
    private Properties _jmsprop = null;

    public XAConnectionFactoryImpl(XAConnectionFactory cf, String ref, boolean isMultiRM) {
        _cf = cf;
        _ref = ref;
        _isMultiRM = isMultiRM;
    }

    public XAConnectionFactoryImpl(BridgeContext bc, Properties jmsprop, boolean isEmbeded, String ref, boolean isMultiRM) throws Exception {
        _bc = bc;
        _jmsprop = jmsprop;
        _cf = bc.getXAConnectionFactory(jmsprop);
        _ref = ref;
        _isEmbeded = isEmbeded;
        _isMultiRM = isMultiRM;
    }

    @Override
    public XAConnection createXAConnection() throws JMSException {
        if (_bc != null) {
            XAConnectionFactory cf = null;
            try {
                cf = _bc.getXAConnectionFactory(_jmsprop);
            } catch (Exception e) {
                JMSException jmse = new JMSException(e.getMessage(), JMSBridge.getJMSBridgeResources().E_EXCEPTION_CREATE_CF, e);
                throw jmse;
            }
            return cf.createXAConnection();
        }
        return _cf.createXAConnection();
    }

    @Override
    public XAConnection createXAConnection(String userName, String password) throws JMSException {
        if (_bc != null) {
            XAConnectionFactory cf = null;
            try {
                cf = _bc.getXAConnectionFactory(_jmsprop);
            } catch (Exception e) {
                JMSException jmse = new JMSException(e.getMessage(), JMSBridge.getJMSBridgeResources().E_EXCEPTION_CREATE_CF, e);
                throw jmse;
            }
            return cf.createXAConnection(userName, password);
        }
        return _cf.createXAConnection(userName, password);
    }

    @Override
    public XAJMSContext createXAContext() {
        if (_bc != null) {
            XAConnectionFactory cf = null;
            try {
                cf = _bc.getXAConnectionFactory(_jmsprop);
            } catch (Exception e) {
                JMSRuntimeException jmse = new MQRuntimeException(e.getMessage(), JMSBridge.getJMSBridgeResources().E_EXCEPTION_CREATE_CF, e);
                throw jmse;
            }
            return cf.createXAContext();
        }
        return _cf.createXAContext();
    }

    @Override
    public XAJMSContext createXAContext(String userName, String password) {
        if (_bc != null) {
            XAConnectionFactory cf = null;
            try {
                cf = _bc.getXAConnectionFactory(_jmsprop);
            } catch (Exception e) {
                JMSRuntimeException jmse = new MQRuntimeException(e.getMessage(), JMSBridge.getJMSBridgeResources().E_EXCEPTION_CREATE_CF, e);
                throw jmse;
            }
            return cf.createXAContext(userName, password);
        }
        return _cf.createXAContext(userName, password);
    }

    @Override
    public String getRef() {
        return _ref;
    }

    @Override
    public Object getRefed() {
        return _cf;
    }

    @Override
    public boolean isEmbeded() {
        return _isEmbeded;
    }

    @Override
    public boolean isMultiRM() {
        return _isMultiRM;
    }

    @Override
    public String toString() {
        String refs = _ref + (_isEmbeded ? ", embeded" : "") + (_isMultiRM ? ", multirm" : "");
        String s = null;
        if (_firstTime) {
            s = "[" + refs + "]" + _cf.toString();
            _firstTime = false;
        } else {
            s = "[" + refs + "]" + _cf.getClass().getName();
        }
        return s;
    }

}
