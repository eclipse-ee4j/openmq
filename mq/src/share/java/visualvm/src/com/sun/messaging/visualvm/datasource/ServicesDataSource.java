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

package com.sun.messaging.visualvm.datasource;

import com.sun.messaging.visualvm.datasource.MQResourceDescriptor.ResourceType;
import com.sun.tools.visualvm.application.Application;

import java.awt.Image;
import org.openide.util.ImageUtilities;

public class ServicesDataSource extends MQDataSource {

    private static final Image NODE_ICON = ImageUtilities.loadImage(
            "com/sun/messaging/visualvm/ui/resources/services.gif", true);

    public ServicesDataSource(Application app, MQDataSource master, String name) {
        super(master);
        application = app;
        this.descriptor = new MQResourceDescriptor(this, name, ResourceType.SERVICES, null, NODE_ICON);
    }
    
}
