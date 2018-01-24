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
#include "atmi.h"	  /* TUXEDO */
#include "userlog.h"  /* TUXEDO */


int main(int argc, char *argv[])
{
	char *recvbuf;
	long rcvlen;
	int ret;
	int cflgs=0;		/* Commit flags, currently unused */
	int aflgs=0;		/* Abort flags, currently unused */

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

        /* start transaction... with 30s timeout, 2nd arg is unused */
	if (tpbegin(60, 0) == -1) {
		(void)fprintf(stderr, "Failed to begin transaction, %s\n",
			tpstrerror(tperrno));
		(void)userlog("Failed to begin transaction, %s",
			tpstrerror(tperrno));
		(void)tpterm();
		exit(1);
	}
	else
	{
		printf("Successfully started transaction\n");
	}

	/* Request the service TOUPPER, waiting for a reply */
	printf("calling tpcall(SENDMESSAGES)\n");
	ret = tpcall("SENDMESSAGES", (char *)NULL, 0, (char **)&recvbuf, &rcvlen, (long)0);
	printf("tpcall(SENDMESSAGES) returned %d\n",ret);

	if (ret < 0)		
        {
		(void) tpabort(aflgs);
		(void) fprintf(stderr, "Can't send request to service SENDMESSAGES. tpcall return value = %d\n", ret);
		(void) fprintf(stderr, "tperrno = %d\n", tperrno);
		(void)fprintf(stderr, "Failed to call SENDMESSAGES, \"%s\n\"",
			tpstrerror(tperrno));
		tpfree(recvbuf);
		(void)tpterm();
		exit(1);
	}
	else 
	{
		if (tpcommit(cflgs) == -1) 
		{
			(void)fprintf(stderr, "Failed to commit transaction, %s\n",
				tpstrerror(tperrno));
			(void)userlog("Failed to commit transaction, %s",
				tpstrerror(tperrno));
			tpfree(recvbuf);
			(void)tpterm();
			exit(1);
		}
		else
		{
			printf("Successfully committed\n");
		}
	}

	/*print out results only when transaction has committed successfully*/
	/*(void) fprintf(stdout, "Returned string is: %s\n", rcvbuf);*/

	/* Free Buffers & Detach from System/T */
	tpfree(recvbuf);
	tpterm();
	return(0);
}
