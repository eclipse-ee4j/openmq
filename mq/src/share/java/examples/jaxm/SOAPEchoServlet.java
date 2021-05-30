/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

import jakarta.servlet.annotation.WebServlet;

import jakarta.xml.messaging.JAXMServlet;
import jakarta.xml.messaging.ReqRespListener;

import jakarta.xml.soap.SOAPMessage;

/**
 * This example echos the SOAP message received back to the sender.
 */
@WebServlet("/SOAPEchoServlet")
public class SOAPEchoServlet extends JAXMServlet implements ReqRespListener {

    /**
     * SOAP Message received is echoed back to the sender.
     */
    public SOAPMessage onMessage (SOAPMessage soapMessage) {
        return soapMessage;
    }

}
