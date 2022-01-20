/*
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

package com.sun.messaging.jmq.jmsserver.config;

import java.util.*;

/**
 * This class is used by updateProperties in place of the normal value for a property to maintain the list of listeners
 * for that property.
 * <P>
 *
 * This class maintains both the value and the set of listeners
 */
class WatchedProperty {
    /**
     * value of the property
     */
    private String value;

    /**
     * set of listeners for the property
     */
    private Vector listeners = null;

    /**
     * create a new watched property object with a null value
     */
    WatchedProperty() {
        this(null);
    }

    /**
     * create a new watched property object
     *
     * @param value value of the property
     */
    WatchedProperty(String value) {
        this.value = value;
        listeners = new Vector();
    }

    /**
     * sets the value of the property object
     *
     * @param value the new value of the property
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * returns the value of the property object
     *
     * @return the current value of the property
     */
    public String getValue() {
        return value;
    }

    /**
     * adds a new config listener
     *
     * @param listener the listener object to add
     */
    public void addListener(ConfigListener listener) {
        listeners.addElement(listener);
    }

    /**
     * removes an existing config listener
     *
     * @param listener the listener object to remove as a listener
     */
    public void removeListener(ConfigListener listener) {
        listeners.removeElement(listener);
    }

    /**
     * clears out all config listener
     *
     */
    void clearListeners() {
        listeners = new Vector();
    }

    /**
     * returns the vector of all config listener
     *
     * @return the vector of listeners (if there are no listeners, it will return an empty vectory NOT a null vector)
     */
    Vector getListeners() {
        return this.listeners;
    }

    /**
     * prints out the string for a watched Property
     */
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("WatchedProperty(").append(listeners.size()).append(") value = [\"").append(value).append("\"] {");
        for (int i = 0; i < listeners.size(); i++) {
            if (i > 0) {
                buf.append(", ");
            }
            buf.append(listeners.elementAt(i).getClass());
        }
        buf.append('}');
        return buf.toString();
    }
}
