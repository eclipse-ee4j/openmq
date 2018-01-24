/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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
 * @(#)com_sun_messaging_jmq_jmsserver_audit_BSMAuditSession.c	1.5 07/02/07
 */ 

#include <pwd.h>
#include <jni.h>
#include <unistd.h>
#include <stdlib.h>
#include <sys/types.h>

#include "com_sun_messaging_jmq_jmsserver_audit_impl_BSMAuditSession.h"

/*
 * Class:     BSMAuditSession
 * Method:    nativeGetUidGid
 * Signature: int[] nativeGetUidGid(String)
 */

JNIEXPORT jintArray JNICALL Java_com_sun_messaging_jmq_jmsserver_audit_BSMAuditSession_nativeGetUidGid (JNIEnv *env, jclass class, jstring juser)
{
    jint* jids;
    jintArray idArray;
    struct passwd *pw;
    const char *user = (*env)->GetStringUTFChars(env, juser, NULL);

    jids = (jint*)malloc(2 *sizeof(jint));
    jids[0] = -1;
    jids[1] = -1;
    if (user != NULL && strlen(user)) {
	pw = getpwnam(user);
	if (pw != NULL) {
	    jids[0] = pw->pw_uid;
	    jids[1] = pw->pw_gid;
	}
    }   

    idArray = (*env)->NewIntArray(env, 2);
    (*env)->SetIntArrayRegion(env, idArray, 0, 2, jids);
    (*env)->ReleaseStringUTFChars(env, juser, user);
    free(jids);

    return idArray;
}

/*
 * Class:     BSMAuditSession
 * Method:    nativeBrokerUidGid
 * Signature: int[] nativeBrokerUidGid()
 */

JNIEXPORT jintArray JNICALL Java_com_sun_messaging_jmq_jmsserver_audit_BSMAuditSession_nativeBrokerUidGid (JNIEnv *env, jclass class)
{
    jint* jids;
    jintArray idArray;

    int ids[2] = { -1, -1 };
    ids[0] = getuid();
    ids[1] = getgid();

    jids = (jint*)malloc(2 *sizeof(jint));
    jids[0] = getuid();
    jids[1] = getgid();
    idArray = (*env)->NewIntArray(env, 2);
    (*env)->SetIntArrayRegion(env, idArray, 0, 2, jids);
    free(jids);

    return idArray;
}

