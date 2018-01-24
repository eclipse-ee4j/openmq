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

import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSException;

import com.sun.messaging.bridge.api.BridgeContext;
import com.sun.messaging.jms.MQRuntimeException;

/**
 * @author amyk
 */
public class ConnectionFactoryImpl implements ConnectionFactory, Refable {

    private ConnectionFactory _cf = null;
    private String _ref = null;
    private boolean _isEmbeded = false;
    private boolean _firstTime = true;

    private BridgeContext _bc = null;
    private Properties _jmsprop = null;
    private boolean _isadmin = false;

    public ConnectionFactoryImpl(ConnectionFactory cf, String ref) {
        _cf = cf;
        _ref = ref;
    }

    public ConnectionFactoryImpl(BridgeContext bc, Properties jmsprop,
                                 boolean isEmbeded, String ref)
                                 throws Exception {
        this(bc, jmsprop, false, isEmbeded, ref);
    }
    public ConnectionFactoryImpl(BridgeContext bc, Properties jmsprop,
                                 boolean isadmin, boolean isEmbeded, String ref)
                                 throws Exception {
        _bc = bc;
        _jmsprop = jmsprop;
        _isadmin = isadmin;
        if (!isadmin) {
            _cf =_bc.getConnectionFactory(_jmsprop);
        } else {
            _cf =_bc.getAdminConnectionFactory(_jmsprop);
        }
        _ref = ref;
        _isEmbeded = isEmbeded;
    }

    public Connection createConnection() throws JMSException {
    	return getConnectionFactory().createConnection();
    }

    public Connection createConnection(String userName, String password) throws JMSException {  	
    	return getConnectionFactory().createConnection(userName, password);
    }
    
	@Override
	public JMSContext createContext() {
		try {
			return getConnectionFactory().createContext();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public JMSContext createContext(String userName, String password) {
		try {
			return getConnectionFactory().createContext(userName,password);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public JMSContext createContext(String userName, String password, int sessionMode) {
		try {
			return getConnectionFactory().createContext(userName,password,sessionMode);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public JMSContext createContext(int sessionMode) {
		try {
			return getConnectionFactory().createContext(sessionMode);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	private ConnectionFactory getConnectionFactory() throws JMSException {
		if (_bc != null) {
	        ConnectionFactory cf = null;
	        try {
	            if (!_isadmin) {
	                cf = _bc.getConnectionFactory(_jmsprop);
	            } else {
	                cf = _bc.getAdminConnectionFactory(_jmsprop);
	            }
	        } catch (Exception e) {
	            JMSException jmse = new JMSException(e.getMessage(),
	                JMSBridge.getJMSBridgeResources().E_EXCEPTION_CREATE_CF);
	            jmse.setLinkedException(e);
	            throw jmse;
	        }
	        return cf;
		}
		return _cf;
	}

    public String getRef() {
        return _ref;
    }

    public Object getRefed() {
        return _cf;
    }

    public boolean isEmbeded() {
        return _isEmbeded;
    }

    public boolean isMultiRM() {
        return false;
    }

    public String toString() {
        String refs = _ref+(_isEmbeded ? ", embeded":"");
        String s = null;
        if (_firstTime) {
            s = "["+refs+"]"+_cf.toString();
            _firstTime = false; 
        } else {
            s = "["+refs+"]"+_cf.getClass().getName();
        }
        return s;
    }

}
