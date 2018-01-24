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

package com.sun.messaging.visualvm.ui;

import java.awt.Frame;
import javax.swing.event.EventListenerList;

/**
 *
 * @author isa
 */
public class BasicDialog extends javax.swing.JDialog {

    private EventListenerList mqUIListeners = new EventListenerList();

    public BasicDialog(Frame owner, String title, boolean modal) {
        super(owner, title, modal);
    }

    public BasicDialog(Frame owner, boolean modal) {
        super(owner, modal);
    }
    
     public void addMQUIEventListener(MQUIEventListener l) {
        mqUIListeners.add(MQUIEventListener.class, l);
    }

    public void removeMQUIEventListener(MQUIEventListener l) {
        mqUIListeners.remove(MQUIEventListener.class, l);
    }

    public void fireMQUIEventDispatched(MQUIEvent me) {
        Object[] l = mqUIListeners.getListenerList();

        for (int i = l.length - 2; i >= 0; i -= 2) {
            if (l[i] == MQUIEventListener.class) {
                ((MQUIEventListener) l[i + 1]).mqUIEventDispatched(me);
            }
        }
    }
}
