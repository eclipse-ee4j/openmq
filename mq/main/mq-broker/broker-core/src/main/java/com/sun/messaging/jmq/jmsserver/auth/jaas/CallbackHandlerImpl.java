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
 * @(#)CallbackHandlerImpl.java	1.4 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth.jaas;

import java.util.Properties;
import java.io.IOException;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.LanguageCallback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.TextInputCallback;
import javax.security.auth.callback.TextOutputCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;

/**
 */

public class CallbackHandlerImpl implements CallbackHandler {

    private static boolean DEBUG = false;

    private BrokerResources rb = Globals.getBrokerResources();
    private transient Logger logger = Globals.getLogger();

    private Properties authProps = null;
    private String userName = null;
    private String password = null;

    /**
     * not used as default handler
     */
    private CallbackHandlerImpl() { }

    public CallbackHandlerImpl(Properties authProps, String userName, String password) {
        this.authProps = authProps;
        this.userName = userName;
        this.password = password;
    }

    protected synchronized void destroy() {
        authProps = null;
        userName = null;
        password = null;
    }

    /**
     * Handles the specified set of callbacks.
     *
     * @param callbacks An array of Callback objects provided by an 
     *                  underlying security service which contains the
     *                  information requested to be retrieved or displayed.
     * @throws IOException If an input or output error occurs 
     * @throws UnsupportedCallbackException If the implementation of this 
     *         method does not support one or more of the Callbacks specified
     *         in the callbacks parameter.
     */
    public synchronized void handle(Callback[] callbacks) throws IOException, 
                                                UnsupportedCallbackException {

	    for (int i = 0; i < callbacks.length; i++) {

	    if (callbacks[i] instanceof LanguageCallback) {
            LanguageCallback cb = (LanguageCallback)callbacks[i];
            if (DEBUG) {
                logger.log(logger.INFO, "JAAS CallbackHander handle LanguageCallback -"+
                           " returning " + rb.getLocale());
            }
            cb.setLocale(rb.getLocale());
            continue;
        } 
        if (callbacks[i] instanceof NameCallback) {
            if (userName == null) { 
                String emsg = Globals.getBrokerResources().getKString(
                       BrokerResources.X_JAAS_CALLBACK_HANDLER_NOT_INITIALIZED,
                       "NameCallback");
                logger.log(logger.ERROR,  emsg);
                throw new UnsupportedCallbackException(callbacks[i], emsg);
            }
            NameCallback cb = (NameCallback)callbacks[i];
            if (DEBUG) {
                logger.log(logger.INFO, "JAAS CallbackHander handle NameCallback prompt: "+
                       ((NameCallback)callbacks[i]).getPrompt()+ " - returning " +userName);
            }
            cb.setName(userName);
            continue;
        }
 	    if (callbacks[i] instanceof PasswordCallback) {
            if (password == null) { 
                String emsg = Globals.getBrokerResources().getKString(
                       BrokerResources.X_JAAS_CALLBACK_HANDLER_NOT_INITIALIZED, 
                       "PasswordCallback");
                logger.log(logger.ERROR,  emsg);
                throw new UnsupportedCallbackException(callbacks[i], emsg);
            }

 		    PasswordCallback cb = (PasswordCallback)callbacks[i];
            if (DEBUG) {
                logger.log(logger.INFO, 
           "JAAS CallbackHander handle PasswordCallback ["+cb.getClass().getName()+"] prompt:"+cb.getPrompt());
            }
 		    cb.setPassword(password.toCharArray());
            continue;
        }
        if (callbacks[i] instanceof TextInputCallback) {
            if (authProps == null) { 
                String emsg = Globals.getBrokerResources().getKString(
                       BrokerResources.X_JAAS_CALLBACK_HANDLER_NOT_INITIALIZED, 
                       "TextInputCallback");
                logger.log(logger.ERROR,  emsg);
                throw new UnsupportedCallbackException(callbacks[i], emsg);
            }

            TextInputCallback cb = (TextInputCallback)callbacks[i];
            String text =  null;
            if (cb.getPrompt().equals(AccessController.PROP_AUTHENTICATION_TYPE)) {
                text = (String)authProps.getProperty(AccessController.PROP_AUTHENTICATION_TYPE); 
            } else if (cb.getPrompt().equals(AccessController.PROP_ACCESSCONTROL_TYPE)) {
                text = (String)authProps.getProperty(AccessController.PROP_ACCESSCONTROL_TYPE); 
            } else if (cb.getPrompt().equals(AccessController.PROP_CLIENTIP)) {
                text = (String)authProps.getProperty(AccessController.PROP_CLIENTIP);
            } else if (cb.getPrompt().equals(AccessController.PROP_SERVICE_NAME)) {
                text = (String)authProps.getProperty(AccessController.PROP_SERVICE_NAME);
            } else if (cb.getPrompt().equals(AccessController.PROP_SERVICE_TYPE)) {
                text = (String)authProps.getProperty(AccessController.PROP_SERVICE_TYPE);
            } else { 
                String emsg = Globals.getBrokerResources().getKString(
                              BrokerResources.W_JAAS_UNSUPPORTED_TEXTINPUTCALLBACK,
                              cb.getClass().getName(), cb.getPrompt());
                logger.log(logger.WARNING, emsg);
                throw new UnsupportedCallbackException(callbacks[i], emsg);
            }
            if (DEBUG) {
                logger.log(logger.INFO, "JAAS CallbackHander handle TextInputCallback prompt: "+
                       ((TextInputCallback)callbacks[i]).getPrompt()+ " - returning " +text);
            }
            cb.setText(text);
            continue; 
        }
        if (callbacks[i] instanceof TextOutputCallback) {
		    int level = logger.OFF;
            TextOutputCallback cb = (TextOutputCallback) callbacks[i];

            switch (cb.getMessageType()) {
            case TextOutputCallback.INFORMATION:
                 level = logger.INFO;
                 break;
            case TextOutputCallback.WARNING:
                 level = logger.WARNING;
                 break;
            case TextOutputCallback.ERROR:
                 level = logger.ERROR;
                 break;
            default:
                String emsg = Globals.getBrokerResources().getKString(
                              BrokerResources.W_JAAS_UNSUPPORTED_TEXTOUTPUTCALLBACK,
                              cb.getClass().getName(), Integer.valueOf(cb.getMessageType()));
                logger.log(logger.WARNING, emsg);
                throw new UnsupportedCallbackException(callbacks[i], emsg);
		    }
            logger.log(level, cb.getClass().getName()+": "+ cb.getMessage());
	        continue;	
        }
        String emsg = Globals.getBrokerResources().getKString(
                      BrokerResources.W_JAAS_UNSUPPORTED_CALLBACK, 
                      callbacks[i].getClass().getName());
        throw new UnsupportedCallbackException(callbacks[i], emsg);
        } //for
	}
	    
}
