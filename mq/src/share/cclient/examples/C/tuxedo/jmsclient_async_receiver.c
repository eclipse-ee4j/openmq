/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

#include <stdio.h>
#include <stdlib.h>
#include "atmi.h"     /* TUXEDO */
#include "userlog.h"  /* TUXEDO */


int main(int argc, char *argv[])
{
	char *recvbuf;
	long rcvlen;
	int ret;

	/* Attach to System/T as a Client Process */
	if (tpinit((TPINIT *) NULL) == -1) 
	{
        (void) fprintf(stderr, "tpinit failed\n");
        exit(1);
	}
	
	/** documentation implies that it is not valid to have
	    odata as NULL, so allocate some arbitrary memory to 
	    keep it happy */
	if((recvbuf = (char *) tpalloc("STRING", NULL, 10)) == NULL) {
		(void) fprintf(stderr,"Error allocating receive buffer\n");
		tpterm();
		exit(1);
	}

	/* Request the service TOUPPER, waiting for a reply */
	printf("calling tpcall(ARECVMESSAGES)\n");
	ret = tpcall("ARECVMESSAGES", (char *)NULL, 0, (char **)&recvbuf, &rcvlen, (long)0);
	printf("tpcall(ARECVMESSAGES) returned %d\n",ret);

    if (ret < 0) {
		(void)fprintf(stderr, "Can't send request to service ARECVMESSAGES. tpcall return value = %d\n", ret);
		(void)fprintf(stderr, "tperrno = %d\n", tperrno);
		(void)fprintf(stderr, "Failed to call ARECVMESSAGES, \"%s\n\"",
			tpstrerror(tperrno));
		tpfree(recvbuf);
		(void)tpterm();
		exit(1);
	}

    printf("ARECVMESSAGES completed !\n");

	/* Free Buffers & Detach from System/T */
	tpfree(recvbuf);
	tpterm();
	return(0);
}
