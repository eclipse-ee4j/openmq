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
 * @(#)HttpsTunnelServlet.java	1.8 06/28/07
 */ 
 
package com.sun.messaging.jmq.httptunnel.tunnel.servlet;

import java.io.PrintWriter;

import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class HttpsTunnelServlet extends HttpTunnelServlet {
    public void init() throws ServletException {
        servletContext = this.getServletContext();
        startTime = new java.util.Date();
        servletName = "HttpsTunnelServlet";

        try {
            linkTable = new ServerLinkTable(this.getServletConfig(), true);
            inService = true;
        } catch (Exception e) {
            // save the exception 
            initException = e;
            servletContext.log(servletName + ": initialization failed, " + e);
        }
    }

    public void handleTest(HttpServletRequest request,
        HttpServletResponse response) {
        try {
            response.setContentType("text/html; charset=UTF-8 ");

            PrintWriter pw = response.getWriter();

            pw.println("<HTML>");

            pw.println("<HEAD>");
            pw.println("<TITLE> JMQ HTTPS Tunneling Servlet </TITLE>");
            pw.println("</HEAD>");

            pw.println("<BODY>");

            if (inService) {
                pw.println("HTTPS tunneling servlet ready.<BR>");
                pw.println("Servlet Start Time : " + startTime + " <BR>");
                pw.println("Accepting secured connections from brokers on " +
                    "port : " + linkTable.getServletPort() + " <P>");

                Vector slist = linkTable.getServerList();
                pw.println("Total available brokers = " + slist.size() +
                    "<BR>");
                pw.println("Broker List : <BR>");

                pw.println("<BLOCKQUOTE><PRE>");

                for (int i = 0; i < slist.size(); i++) {
                    pw.println((String) slist.elementAt(i));
                }

                pw.println("</PRE></BLOCKQUOTE>");
            } else {
                pw.println(new java.util.Date() + "<br>");
                pw.println("HTTPS Tunneling servlet cannot be started.<br>");

                if (initException != null) {
                    pw.println("    " + initException);
                }
            }

            pw.println("</BODY>");
            pw.println("</HTML>");
        } catch (Exception e) {
        }
    }
}
