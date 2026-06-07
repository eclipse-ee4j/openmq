/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Contributors to Eclipse Foundation. All rights reserved.
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

package com.sun.messaging.jmq.admin.apps.objmgr;

import java.util.Properties;

import com.sun.messaging.InvalidPropertyException;
import com.sun.messaging.InvalidPropertyValueException;
import com.sun.messaging.ReadOnlyPropertyException;
import com.sun.messaging.jmq.admin.objstore.ObjStoreAttrs;
import com.sun.messaging.jmq.admin.util.CommonGlobals;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.util.JMSObjFactory;
import com.sun.messaging.jmq.admin.resources.AdminResources;
import com.sun.messaging.AdministeredObject;

/**
 * This class contains the logic to execute previewing of the user commands specified in the ObjMgrProperties object. It
 * has one public entry point which is the previewCommands() method.
 *
 * @see ObjMgr
 *
 */
public class CmdPreviewer implements ObjMgrOptions {

    private AdminResources ar = Globals.getAdminResources();
    private ObjMgrProperties objMgrProps;

    public CmdPreviewer(ObjMgrProperties props) {
        this.objMgrProps = props;
    }

    /*
     * Run/execute the user commands specified in the ObjMgrProperties object.
     */
    public void previewCommands() {
        /*
         * Determine type of command and invoke the relevant preview method.
         */
        CommonGlobals.stdOutPrintln(ar.getString(ar.I_PREVIEW_ON));

        String cmd = objMgrProps.getCommand();
        if (cmd.equals(OBJMGR_ADD_PROP_VALUE)) {
            previewAddCommand(objMgrProps);
        } else if (cmd.equals(OBJMGR_DELETE_PROP_VALUE)) {
            previewDeleteCommand(objMgrProps);
        } else if (cmd.equals(OBJMGR_QUERY_PROP_VALUE)) {
            previewQueryCommand(objMgrProps);
        } else if (cmd.equals(OBJMGR_LIST_PROP_VALUE)) {
            previewListCommand(objMgrProps);
        } else if (cmd.equals(OBJMGR_UPDATE_PROP_VALUE)) {
            previewUpdateCommand(objMgrProps);
        }
    }

    /*
     * Preview add command
     */
    private void previewAddCommand(ObjMgrProperties objMgrProps) {
        /*
         * Get object type, props object, and lookup name
         */
        String type = objMgrProps.getObjType();
        Properties objProps = objMgrProps.getObjProperties();
        ObjStoreAttrs osa = objMgrProps.getObjStoreAttrs();
        String lookupName = objMgrProps.getLookupName();

        /*
         * Check if -f (force) was specified on cmd line.
         */
        boolean force = objMgrProps.forceModeSet();

        /*
         * Create JMS Object with the specified properties.
         */
        Object newObj = null;

        try {
            if (type.equals(OBJMGR_TYPE_QUEUE)) {
                newObj = JMSObjFactory.createQueue(objProps);
            } else if (type.equals(OBJMGR_TYPE_TOPIC)) {
                newObj = JMSObjFactory.createTopic(objProps);
            } else if (type.equals(OBJMGR_TYPE_XQCF)) {
                newObj = JMSObjFactory.createXAQueueConnectionFactory(objProps);
            } else if (type.equals(OBJMGR_TYPE_XTCF)) {
                newObj = JMSObjFactory.createXATopicConnectionFactory(objProps);
            } else if (type.equals(OBJMGR_TYPE_XCF)) {
                newObj = JMSObjFactory.createXAConnectionFactory(objProps);
            } else if (type.equals(OBJMGR_TYPE_QCF)) {
                newObj = JMSObjFactory.createQueueConnectionFactory(objProps);
            } else if (type.equals(OBJMGR_TYPE_TCF)) {
                newObj = JMSObjFactory.createTopicConnectionFactory(objProps);
            } else if (type.equals(OBJMGR_TYPE_CF)) {
                newObj = JMSObjFactory.createConnectionFactory(objProps);
            }
        } catch (Exception e) {
            handleRunCommandExceptions(e, lookupName);
            CommonGlobals.stdOutPrintln("");
            CommonGlobals.stdOutPrintln(ar.getString(ar.I_OBJ_PREV_ADD_FAILED));
            return;
        }

        if (force) {
            CommonGlobals.stdOutPrintln(ar.getString(ar.I_PROMPT_OFF));
        } else {
            CommonGlobals.stdOutPrintln(ar.getString(ar.I_PROMPT_ON));
        }

        CommonGlobals.stdOutPrintln("");
        CommonGlobals.stdOutPrintln(ar.getString(ar.I_PREVIEW_ADD, Utils.getObjTypeString(type)));
        CommonGlobals.stdOutPrintln("");

        ObjMgrPrinter omp = new ObjMgrPrinter(2, 4);
        omp.printObjPropertiesFromObj((AdministeredObject) newObj);
        CommonGlobals.stdOutPrintln("");

        ObjMgrPrinter.printReadOnly(objMgrProps.readOnlyValue());
        CommonGlobals.stdOutPrintln("");

        CommonGlobals.stdOutPrintln(ar.getString(ar.I_ADD_CMD_DESC_LOOKUP));
        CommonGlobals.stdOutPrintln("");
        CommonGlobals.stdOutPrintln(lookupName);
        CommonGlobals.stdOutPrintln("");

        CommonGlobals.stdOutPrintln(ar.getString(ar.I_ADD_CMD_DESC_STORE));
        CommonGlobals.stdOutPrintln("");

        ObjMgrPrinter omp2 = new ObjMgrPrinter(osa, 2, 4);
        omp2.print();
        CommonGlobals.stdOutPrintln("");

        CommonGlobals.stdOutPrintln(ar.getString(ar.I_OBJ_NOT_ADDED));
    }

    /*
     * Preview delete command
     */
    private void previewDeleteCommand(ObjMgrProperties objMgrProps) {
        /*
         * Get object type, props object, and lookup name
         */
        // String type = objMgrProps.getObjType();
        // Properties objProps = objMgrProps.getObjProperties();
        ObjStoreAttrs osa = objMgrProps.getObjStoreAttrs();
        String lookupName = objMgrProps.getLookupName();

        /*
         * Check if -f (force) was specified on cmd line.
         */
        boolean force = objMgrProps.forceModeSet();

        if (force) {
            CommonGlobals.stdOutPrintln(ar.getString(ar.I_PROMPT_OFF));
        } else {
            CommonGlobals.stdOutPrintln(ar.getString(ar.I_PROMPT_ON));
        }

        CommonGlobals.stdOutPrintln("");
        CommonGlobals.stdOutPrintln(ar.getString(ar.I_PREVIEW_DELETE));
        CommonGlobals.stdOutPrintln("");
        CommonGlobals.stdOutPrintln(lookupName);
        CommonGlobals.stdOutPrintln("");

        CommonGlobals.stdOutPrintln(ar.getString(ar.I_DELETE_CMD_DESC_STORE));
        CommonGlobals.stdOutPrintln("");

        ObjMgrPrinter omp = new ObjMgrPrinter(osa, 2, 4);
        omp.print();
        CommonGlobals.stdOutPrintln("");

        CommonGlobals.stdOutPrintln(ar.getString(ar.I_OBJ_NOT_DELETED));

    }

    /*
     * Preview query command
     */
    private void previewQueryCommand(ObjMgrProperties objMgrProps) {
        /*
         * Get object type, props object, and lookup name
         */
        // String type = objMgrProps.getObjType();
        ObjStoreAttrs osa = objMgrProps.getObjStoreAttrs();
        String lookupName = objMgrProps.getLookupName();

        CommonGlobals.stdOutPrintln(ar.getString(ar.I_PREVIEW_QUERY));
        CommonGlobals.stdOutPrintln("");
        CommonGlobals.stdOutPrintln(lookupName);
        CommonGlobals.stdOutPrintln("");

        CommonGlobals.stdOutPrintln(ar.getString(ar.I_QUERY_CMD_DESC_STORE));
        CommonGlobals.stdOutPrintln("");

        ObjMgrPrinter omp = new ObjMgrPrinter(osa, 2, 4);
        omp.print();
        CommonGlobals.stdOutPrintln("");

        CommonGlobals.stdOutPrintln(ar.getString(ar.I_OBJ_NOT_QUERIED));
    }

    /*
     * Preview list command
     */
    private void previewListCommand(ObjMgrProperties objMgrProps) {
        /*
         * Get object type, props object, and lookup name
         */
        String type = objMgrProps.getObjType();
        ObjStoreAttrs osa = objMgrProps.getObjStoreAttrs();
        // String lookupName = objMgrProps.getLookupName();

        String typeString = Utils.getObjTypeString(type);

        if (typeString != null) {
            CommonGlobals.stdOutPrintln(ar.getString(ar.I_PREVIEW_LIST_TYPE, typeString));
        } else {
            CommonGlobals.stdOutPrintln(ar.getString(ar.I_PREVIEW_LIST));
        }

        CommonGlobals.stdOutPrintln("");

        ObjMgrPrinter omp = new ObjMgrPrinter(osa, 2, 4);
        omp.print();
        CommonGlobals.stdOutPrintln("");

        CommonGlobals.stdOutPrintln(ar.getString(ar.I_OBJ_NOT_LISTED));
    }

    /*
     * Preview update command
     */
    private void previewUpdateCommand(ObjMgrProperties objMgrProps) {
        /*
         * Get object type, props object, and lookup name
         */
        String type = objMgrProps.getObjType();
        Properties objProps = objMgrProps.getObjProperties();
        ObjStoreAttrs osa = objMgrProps.getObjStoreAttrs();
        String lookupName = objMgrProps.getLookupName();

        /*
         * Check if -f (force) was specified on cmd line.
         */
        boolean force = objMgrProps.forceModeSet();

        String typeString = Utils.getObjTypeString(type);

        if (force) {
            CommonGlobals.stdOutPrintln(ar.getString(ar.I_PROMPT_OFF));
        } else {
            CommonGlobals.stdOutPrintln(ar.getString(ar.I_PROMPT_ON));
        }

        CommonGlobals.stdOutPrintln("");

        if (typeString != null) {
            /*
             * Create JMS Object with the specified properties. We don't really need to print all the object's attributes since we
             * only want to show the attributes that will be updated. These attributes are stored in 'objProps'. The JMS Object is
             * created here so we can get a hold of the attribute/property labels.
             */
            try {
                if (type.equals(OBJMGR_TYPE_QUEUE)) {
                    JMSObjFactory.createQueue(objProps);
                } else if (type.equals(OBJMGR_TYPE_TOPIC)) {
                    JMSObjFactory.createTopic(objProps);
                } else if (type.equals(OBJMGR_TYPE_XQCF)) {
                    JMSObjFactory.createXAQueueConnectionFactory(objProps);
                } else if (type.equals(OBJMGR_TYPE_XTCF)) {
                    JMSObjFactory.createXATopicConnectionFactory(objProps);
                } else if (type.equals(OBJMGR_TYPE_XCF)) {
                    JMSObjFactory.createXAConnectionFactory(objProps);
                } else if (type.equals(OBJMGR_TYPE_QCF)) {
                    JMSObjFactory.createQueueConnectionFactory(objProps);
                } else if (type.equals(OBJMGR_TYPE_TCF)) {
                    JMSObjFactory.createTopicConnectionFactory(objProps);
                } else if (type.equals(OBJMGR_TYPE_CF)) {
                    JMSObjFactory.createConnectionFactory(objProps);
                }
            } catch (Exception e) {
                handleRunCommandExceptions(e, lookupName);
                CommonGlobals.stdOutPrintln("");
                CommonGlobals.stdOutPrintln(ar.getString(ar.I_OBJ_PREV_UPDATE_FAILED));
                return;
            }

            CommonGlobals.stdOutPrintln(ar.getString(ar.I_PREVIEW_UPDATE_TYPE, typeString));

            CommonGlobals.stdOutPrintln("");

            ObjMgrPrinter omp = new ObjMgrPrinter(objProps, 2, 4);
            omp.print();

        } else {
            CommonGlobals.stdOutPrintln(ar.getString(ar.I_PREVIEW_UPDATE));
            CommonGlobals.stdOutPrintln("");

            ObjMgrPrinter omp = new ObjMgrPrinter(objProps, 2, 4);
            omp.print();
        }
        CommonGlobals.stdOutPrintln("");
        ObjMgrPrinter.printReadOnly(objMgrProps.readOnlyValue());
        CommonGlobals.stdOutPrintln("");

        CommonGlobals.stdOutPrintln(ar.getString(ar.I_WITH_LOOKUP_NAME));
        CommonGlobals.stdOutPrintln("");
        CommonGlobals.stdOutPrintln(lookupName);
        CommonGlobals.stdOutPrintln("");

        CommonGlobals.stdOutPrintln(ar.getString(ar.I_UPDATE_CMD_DESC_STORE));
        CommonGlobals.stdOutPrintln("");

        ObjMgrPrinter omp = new ObjMgrPrinter(osa, 2, 4);
        omp.print();
        CommonGlobals.stdOutPrintln("");

        CommonGlobals.stdOutPrintln(ar.getString(ar.I_OBJ_NOT_UPDATED));
    }

    private void handleRunCommandExceptions(Exception e, String lookupName) {

        if (e instanceof InvalidPropertyException) {
            CommonGlobals.stdErrPrintln(ar.getString(ar.I_ERROR_MESG), ar.getKString(ar.E_INVALID_PROPNAME, e.getMessage()));
            CommonGlobals.stdErrPrintln("");
            CommonGlobals.stdErrPrintln(ar.getString(ar.I_VALID_PROPNAMES));

        } else if (e instanceof InvalidPropertyValueException) {
            CommonGlobals.stdErrPrintln(ar.getString(ar.I_ERROR_MESG), ar.getKString(ar.E_INVALID_PROP_VALUE, e.getMessage()));

        } else if (e instanceof ReadOnlyPropertyException) {
            CommonGlobals.stdErrPrintln(ar.getString(ar.I_ERROR_MESG), ar.getKString(ar.E_CANT_MOD_READONLY));
        }
    }

}
