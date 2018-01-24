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
#include <atmi.h>      /* TUXEDO */
#include <userlog.h>   /* TUXEDO */
#include <mqcrt.h> 


#define MQ_ERR_CHK(mqCall)                             \
  if (MQStatusIsError(status = (mqCall)) == MQ_TRUE) { \
    goto Cleanup;                                      \
  }


/* 
 * This function performs the actual service requested by the client.
 * Its argument is a structure containing among other things a pointer
 * to the data buffer, and the length of the data buffer.
 */

/* for this example we ignore rqst */

void
SENDMESSAGES(TPSVCINFO *rqst)
{
    MQConnectionHandle connection = MQ_INVALID_HANDLE;
    MQSessionHandle session = MQ_INVALID_HANDLE;
    MQDestinationHandle queue = MQ_INVALID_HANDLE;
    MQProducerHandle producer = MQ_INVALID_HANDLE;
    MQMessageHandle message = MQ_INVALID_HANDLE;
    MQStatus status;

    int maxNumMsgs = 10;
    ConstMQString text = "This is a message";


    printf("jmsserver: SENDMESSAGES started\n");

    /* Get XA Connection */

    MQ_ERR_CHK( MQGetXAConnection(&connection) );

    /* Create a XA Session if in transaction else create a regular Session */

    if (tpgetlev() != 0) {
        printf("jmsserver: Creating XA session\n");
        MQ_ERR_CHK( MQCreateXASession(connection, MQ_SESSION_SYNC_RECEIVE,
                                      NULL, NULL, NULL, &session) );
    } else {
        printf("jmsserver: Creating non-XA session \n");
        MQ_ERR_CHK( MQCreateSession(connection, MQ_FALSE, MQ_AUTO_ACKNOWLEDGE,
                                    MQ_SESSION_SYNC_RECEIVE, &session) );
    }
    printf("jmsserver: Created Session successfully\n");

    /* Create a Destination */

    MQ_ERR_CHK( MQCreateDestination(session, "xatestqueue", MQ_QUEUE_DESTINATION, &queue) );
    printf("jmsserver: Created destination successfully\n");

    /* Create a Message Producer */

	MQ_ERR_CHK( MQCreateMessageProducerForDestination(session, queue, &producer) );
    printf("jmsserver: Created producer successfully\n");

    MQ_ERR_CHK( MQFreeDestination(queue) );


	/* Send Messages */

    for (int i = 0; i < maxNumMsgs; i++) {

        MQ_ERR_CHK( MQCreateTextMessage(&message) );
        if (i == (maxNumMsgs -1)) {
		    MQ_ERR_CHK( MQSetTextMessageText(message, "END") );
        } else {
		    MQ_ERR_CHK( MQSetTextMessageText(message, text) );
        }
        printf("jmsserver: Sending message i=%d\n", i);
		MQ_ERR_CHK( MQSendMessage(producer, message) );
        printf("jmsserver: Sent message i=%d\n", i);
		MQ_ERR_CHK( MQFreeMessage(message) );
	}

    /* Close the Session */

	MQ_ERR_CHK( MQCloseSession(session) );

	printf("jmsserver: SENDMESSAGES end\n");

	tpreturn(TPSUCCESS, MQ_OK, NULL, 0L, 0);

Cleanup:    
    {
    MQString estr = MQGetStatusString(status);
    printf("jmsserver: Error: %s\n", (estr == NULL) ? "NULL":estr);
    MQFreeString(estr);
    }
    MQCloseSession(session);
    MQFreeDestination(queue);
    MQFreeMessage(message);

    tpreturn(TPFAIL, -1, NULL, 0L, 0);
}


void
RECVMESSAGES(TPSVCINFO *rqst)
{
    MQConnectionHandle connection = MQ_INVALID_HANDLE;
    MQSessionHandle session = MQ_INVALID_HANDLE;
    MQDestinationHandle queue = MQ_INVALID_HANDLE;
    MQConsumerHandle consumer = MQ_INVALID_HANDLE;
    MQMessageHandle message = MQ_INVALID_HANDLE;
    MQStatus status;

    ConstMQString text;
	int maxNumMsgs = 10;

    printf("jmsserver: RECVMESSAGES started\n");

    /* Get XA Connection */

    MQ_ERR_CHK( MQGetXAConnection(&connection) );

    /* Create a XA Session if in transaction else create a regular Session */

    if (tpgetlev() != 0) {
        printf("jmsserver: Creating XA session\n");
        MQ_ERR_CHK( MQCreateXASession(connection, MQ_SESSION_SYNC_RECEIVE,
                                                NULL, NULL, NULL, &session) );
    } else {
	    printf("jmsserver: Creating non-XA session\n");
        MQ_ERR_CHK( MQCreateSession(connection, MQ_FALSE, MQ_AUTO_ACKNOWLEDGE,
                                             MQ_SESSION_SYNC_RECEIVE, &session) );
    }
    printf("jmsserver: Created session successfully\n");

    /* Create a Destination */
    MQ_ERR_CHK( MQCreateDestination(session,"xatestqueue", MQ_QUEUE_DESTINATION, &queue) );
    printf("jmsserver: Created queue successfully\n");

    /* Create a Message Consumer */

    MQ_ERR_CHK( MQCreateMessageConsumer(session, queue, NULL, MQ_FALSE, &consumer) );
    printf("jmsserver: Created consumer successfully\n");

    MQ_ERR_CHK( MQFreeDestination(queue) );

    /* Start the Connection */

	MQ_ERR_CHK( MQStartConnection(connection) );
	printf("jmsserver: Started connection successfully\n");

    /* Receive Messages */

    for (int i = 0; i < maxNumMsgs; i++) {
        printf("jmsserver: Waiting (30sec) for messages ...\n");
        MQ_ERR_CHK( MQReceiveMessageWithTimeout(consumer, 30000, &message) );
        printf("jmsserver: Received %dth message: ", i);
        MQ_ERR_CHK( MQGetTextMessageText(message, &text) );
        printf("%s\n", text);
		MQ_ERR_CHK( MQFreeMessage(message) );
    }

    /* Close the Session */

	MQ_ERR_CHK( MQCloseSession(session) );
    printf("Closed session successfully\n");

    printf("jmsserver: RECVMESSAGES end\n");

    tpreturn(TPSUCCESS, MQ_OK, NULL, 0L, 0);

Cleanup:
    {
    MQString estr = MQGetStatusString(status);
    printf("jmsserver: Error: %s\n", (estr == NULL) ? "NULL":estr);
    MQFreeString(estr);
    }
    MQCloseSession(session);
    MQFreeMessage(message);
    MQFreeDestination(queue);

    tpreturn(TPFAIL, -1, NULL, 0L, 0);
}

