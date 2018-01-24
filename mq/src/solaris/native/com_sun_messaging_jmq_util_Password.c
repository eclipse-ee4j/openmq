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
#include <stdlib.h>
#include "com_sun_messaging_jmq_util_Password.h"
#if defined(HPUX)
#include <string.h>
#include <sys/termios.h>
#endif
#if defined(AIX)
#include <string.h>
#include <termios.h>
#endif

/*
 * @(#)com_sun_messaging_jmq_util_Password.c	1.9 07/02/07
 */ 

/*
 * Class:     Password
 * Method:    getHiddenPassword
 * Signature: ()Ljava/lang/String;
 */

JNIEXPORT jstring JNICALL Java_com_sun_messaging_jmq_util_Password_getHiddenPassword (JNIEnv *env, jobject obj)  {
    char	*buf;

#if defined(HPUX) || defined(AIX)
    struct termios termio;
    int res;
    char pbuf[257];
    if( (res = tcgetattr( 1, &termio)) )
    {
	return NULL;
    }
    termio.c_lflag &= ~ECHO;
    if( (res = tcsetattr( 1, TCSANOW, &termio)) )
    {
        return NULL;
    }
    if (fgets(pbuf,256,stdin) == NULL)
    {
	buf = NULL;
    }
    else
    {
	char *tmp;
        tmp = strchr(pbuf,'\n');
        if (tmp) *tmp = '\0';
        tmp = strchr(pbuf,'\r');
        if (tmp) *tmp = '\0';
        buf = strdup(pbuf);
    }
    if( (res = tcgetattr( 1, &termio)) )
    {
        return NULL;
    }
    termio.c_lflag |= ECHO;
    if( (res = tcsetattr( 1, TCSANOW, &termio)) )
    {
        return NULL;
    }
#else
    buf = (char *)getpassphrase("");
#endif


    return((*env)->NewStringUTF(env, buf));
}


