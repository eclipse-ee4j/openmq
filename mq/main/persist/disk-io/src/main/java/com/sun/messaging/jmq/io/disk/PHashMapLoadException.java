/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.io.disk;

import java.io.Serial;

/**
 * This class provides information about problems and/or data corruptions encountered when loading the a VRecord from
 * the backing file. If the key and/or the value of the hash map entry is loaded successfully, it can be retrieved by
 * calling <code>getKey()</code> and/or <code>getValue</code> respectively. The throwable caught while deserializing the
 * key can be retrieved by calling <code>getKeyCause()</code>. Similarly, the throwable caught while deserializing the
 * value can be retrieved by calling <code>getValueCause()</code>. Other exception caught while parsing the record, if
 * any, can be retrieved by <code>getCause</code>. <code>getNextException()</code> returns the next chained exception
 * for other loading problems or <code>null</code> if there's no more chained exception.
 */

public class PHashMapLoadException extends Exception {

    @Serial
    private static final long serialVersionUID = -8497350378319307782L;
    private Object key = null;
    private Object value = null;
    private PHashMapLoadException next = null;
    private Throwable keyCause = null;
    private Throwable valueCause = null;

    public PHashMapLoadException(String msg) {
        super(msg);
    }

    public void setKey(Object k) {
        this.key = k;
    }

    /**
     * Return the key of the HashMap entry loaded from file.
     */
    public Object getKey() {
        return key;
    }

    public void setValue(Object v) {
        this.value = v;
    }

    /**
     * Return the value of the HashMap entry loaded from file.
     */
    public Object getValue() {
        return value;
    }

    public void setKeyCause(Throwable t) {
        this.keyCause = t;
    }

    /**
     * Return the Throwable caught while deserializing the key.
     */
    public Throwable getKeyCause() {
        return this.keyCause;
    }

    public void setValueCause(Throwable t) {
        this.valueCause = t;
    }

    /**
     * Return the Throwable caught while deserializing the value.
     */
    public Throwable getValueCause() {
        return this.valueCause;
    }

    public void setNextException(PHashMapLoadException e) {
        this.next = e;
    }

    /**
     * Return the exception chained to this object.
     */
    public PHashMapLoadException getNextException() {
        return next;
    }

    @Override
    public String toString() {
        return getMessage() + "\nkey=" + key + ";cause=" + keyCause + ";value=" + value + ";cause=" + valueCause;
    }
}
