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

package com.sun.messaging.jmq.util.selector;

import java.io.Serial;

/**
 * Exception thrown when an invlaid selector is encoutnered.
 *
 */
public class SelectorFormatException extends java.lang.Exception {

    @Serial
    private static final long serialVersionUID = -2447114466168361869L;
    String selector = null;
    int index = -1;

    public SelectorFormatException() {
    }

    public SelectorFormatException(String message) {
        super(message);
    }

    public SelectorFormatException(String message, String selector) {
        super(message);
        this.selector = selector;
    }

    public SelectorFormatException(String message, String selector, int index) {
        super(message);
        this.selector = selector;
        this.index = index;
    }

    public String getSelector() {
        return selector;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public String getMessage() {

        if (selector != null) {
            if (index > -1) {
                return super.getMessage() + ": \"" + selector + "\" at pos=" + index;
            } else {
                return super.getMessage() + ": \"" + selector + "\"";
            }
        } else {
            return super.getMessage();
        }
    }
}
