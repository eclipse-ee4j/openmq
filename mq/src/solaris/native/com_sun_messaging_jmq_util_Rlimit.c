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

#include <jni.h>
#include <unistd.h>
#include <sys/resource.h>
#include "com_sun_messaging_jmq_util_Rlimit.h"

/*
 * @(#)com_sun_messaging_jmq_util_Rlimit.c	1.3 07/02/07
 */ 

/*
 * Class:     Rlimit
 * Method:    nativeGetRlimit
 */

JNIEXPORT jobject JNICALL Java_com_sun_messaging_jmq_util_Rlimit_nativeGetRlimit (JNIEnv *env, jobject obj, jint resource )  {

    int rcode;
    struct rlimit rl;
    jclass limitClass = NULL;
    jobject limitObject = NULL;
    jfieldID id = NULL;;

    /*
     * XXX REVISIT 3/19/02 dipol: 'resource' is defined by the Rlimit class
     * and uses the Solaris values. Other versions of Unix may use different
     * values (and Linux does). If we ever port this to other versions of
     * Unix then we must map the passed resource value to the appropriate
     * native value.
     */
    rcode = getrlimit((int)resource, &rl);

    if (rcode != 0) {
        /* should throw an exception */
        return NULL;
    }

    limitClass =
        (*env)->FindClass(env, "com/sun/messaging/jmq/util/Rlimit$Limits");

    if (limitClass != NULL) {
        limitObject = (*env)->AllocObject(env, limitClass);
    } else {
        return NULL;
    }

    if (limitObject != NULL) {
        id = (*env)->GetFieldID(env, limitClass, "current", "J");
        if (id != NULL) {
            if (rl.rlim_cur == RLIM_INFINITY) {
                (*env)->SetLongField(env, limitObject, id,
                    (jlong)com_sun_messaging_jmq_util_Rlimit_RLIM_INFINITY);
            } else {
                (*env)->SetLongField(env, limitObject, id,
                    (jlong)(rl.rlim_cur));
            }
        }
    
        id = (*env)->GetFieldID(env, limitClass, "maximum", "J");
        if (id != NULL) {
            if (rl.rlim_max == RLIM_INFINITY) {
                (*env)->SetLongField(env, limitObject, id,
                    (jlong)com_sun_messaging_jmq_util_Rlimit_RLIM_INFINITY);
            } else {
                (*env)->SetLongField(env, limitObject, id,
                    (jlong)(rl.rlim_max));
            }
        }
    }

    return limitObject;
}
