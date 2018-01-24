/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

import javax.xml.messaging.JAXMServlet;
import javax.xml.messaging.ReqRespListener;

import javax.xml.soap.SOAPMessage;

/**
 * This example echos the SOAP message received back to the sender.
 */
public class SOAPEchoServlet extends JAXMServlet implements ReqRespListener {

    /**
     * SOAP Message received is echoed back to the sender.
     */
    public SOAPMessage onMessage (SOAPMessage soapMessage) {
        return soapMessage;
    }

}
