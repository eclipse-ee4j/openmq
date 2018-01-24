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
 * @(#)SelectorFilter.java	1.12 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.core;

import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.lists.Filter;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.io.Packet;
import java.lang.ref.*;
import com.sun.messaging.jmq.util.selector.*;
import java.util.Hashtable;
import java.util.Map;
import java.io.IOException;

public class SelectorFilter implements Filter
{
    private static boolean DEBUG = false;
    Selector selector = null;
    String selectorstr = null;

    public SelectorFilter(String selectorstr) 
         throws SelectorFormatException
    {
        this.selectorstr = selectorstr;
        selector =  Selector.compile(selectorstr);
     
    }
    public SelectorFilter(String selectorstr, Selector sel) 
    {
        this.selectorstr = selectorstr;
        this.selector = sel;
    }

    public synchronized boolean matches(Object o) 
    {
        if (selector == null) {
            return false;
        }
        if (o instanceof PacketReference) {
            PacketReference ref = (PacketReference) o;
            Map props = null;
            Map headers = null;
            try {
                // As an optimization, only extract these if the
                // selector needs them.
                if (selector.usesProperties()) {
                   props = ref.getProperties();
                }
                if (selector.usesFields()) {
                    headers = ref.getHeaders();
                }
            } catch (ClassNotFoundException ex) {
                // this is not a valid error
                assert false : ref;
                throw new RuntimeException("error with properties",
                     ex);
            }
            try {
                boolean match =  selector.match(props, headers);
                if (DEBUG && match)
                    Globals.getLogger().log(Logger.DEBUG,"Match " 
                            + o + "against " + selector + " got " + match);
                return match;
            } catch (SelectorFormatException ex) {
                Globals.getLogger().logStack(Logger.ERROR, 
                    Globals.getBrokerResources().getKString(
                    Globals.getBrokerResources().X_BAD_SELECTOR, 
                    selector, ex.getMessage()), ex);
                return false;
            }
        }
        assert false : " weird ";
        return false;
    }

    public String toString() {
        return "SelectorFilter["+selector+"]"+hashCode();
    }


}
