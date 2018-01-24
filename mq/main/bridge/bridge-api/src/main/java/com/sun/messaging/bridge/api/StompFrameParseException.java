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

package com.sun.messaging.bridge.api;

import com.sun.messaging.jmq.util.LoggerWrapper;

/**
 * @author amyk
 */

public class StompFrameParseException extends Exception {

    public static final StompFrameMessage OOMMSG = getOOMMSG();

    private static StompFrameMessage getOOMMSG() {
        StompFrameMessage msg = StompFrameMessage.newStompFrameMessageERROR();
        msg.setFatalERROR();
        msg.addHeader(StompFrameMessage.ErrorHeader.MESSAGE, "OutOfMemory");
        msg.setNextParseStage(StompFrameMessage.ParseStage.DONE);
        return msg;
    }

    private boolean _fatal = false;

    public StompFrameParseException(String message) {
        this(message, false);
    }

    public StompFrameParseException(String message, boolean fatal) {
        super(message);
        _fatal = fatal;
    }

    public StompFrameParseException(String message, Throwable t) {
        this(message, t, false);
    }

    public StompFrameParseException(String message, Throwable t, boolean fatal) {
        super(message, t);
        _fatal = fatal;
    }

    public boolean isFatal() {
        return _fatal;
    }

    /**
     */
    public StompFrameMessage getStompMessageERROR(
        StompFrameMessageFactory factory, LoggerWrapper logger)
        throws Exception {

        StompFrameMessage msg = factory.newStompFrameMessage(
                                StompFrameMessage.Command.ERROR, logger);
        if (_fatal) {
            msg.setFatalERROR();
        }
        msg.addHeader(StompFrameMessage.ErrorHeader.MESSAGE, getMessage());
        msg.writeExceptionToBody(this);
        msg.setNextParseStage(StompFrameMessage.ParseStage.DONE);

        return msg;
    }
}
