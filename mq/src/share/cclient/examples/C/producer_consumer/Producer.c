/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

/*
 * @(#)Producer.c	1.41 06/26/07
 */ 

/* 
 ***********************************************************
 * C sample program: Producer.c                                      
 * 
 * Description:
 *
 * A message producer
 *
 * By default the destination type is topic. See usage for options. 
 *
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "mqcrt.h"

#define MQ_ERR_CHK(mqCall)                             \
  if (MQStatusIsError(status = (mqCall)) == MQ_TRUE) { \
    goto Cleanup;                                      \
  }


MQStatus
producer(char * brokerHost, int brokerPort,
         char * destinationName, MQDestinationType destinationType)
{
  MQStatus status;
  MQInt32 index;

  /* Declare handles and initialize them */

  MQPropertiesHandle propertiesHandle = MQ_INVALID_HANDLE;
  MQConnectionHandle connectionHandle = MQ_INVALID_HANDLE;
  MQSessionHandle sessionHandle = MQ_INVALID_HANDLE;
  MQDestinationHandle destinationHandle = MQ_INVALID_HANDLE;
  MQProducerHandle producerHandle = MQ_INVALID_HANDLE;
  MQMessageHandle messageHandle = MQ_INVALID_HANDLE;
  MQPropertiesHandle headersHandle = MQ_INVALID_HANDLE;


  /* Setup connection properties */

  MQ_ERR_CHK( MQCreateProperties(&propertiesHandle) );
  MQ_ERR_CHK( MQSetStringProperty(propertiesHandle, 
                                  MQ_BROKER_HOST_PROPERTY, brokerHost) ); 
  MQ_ERR_CHK( MQSetInt32Property(propertiesHandle, 
                                 MQ_BROKER_PORT_PROPERTY, brokerPort) );
  MQ_ERR_CHK( MQSetStringProperty(propertiesHandle,
                                  MQ_CONNECTION_TYPE_PROPERTY, "TCP") );

  /* Create a connection to Message Queue broker */

  MQ_ERR_CHK( MQCreateConnection(propertiesHandle, "guest", "guest", NULL, 
                                 NULL, NULL, &connectionHandle) );

  /* Create a session */ 

  MQ_ERR_CHK( MQCreateSession(connectionHandle, MQ_FALSE, MQ_CLIENT_ACKNOWLEDGE,
                              MQ_SESSION_SYNC_RECEIVE, &sessionHandle) );

  /* Create a destination */

  MQ_ERR_CHK( MQCreateDestination(sessionHandle, destinationName,
                                  destinationType, &destinationHandle) );

  /* Create a messagse producer for the destination */

  MQ_ERR_CHK( MQCreateMessageProducerForDestination(sessionHandle,
                                  destinationHandle, &producerHandle) );

  /* Free the destination handle */

  MQ_ERR_CHK( MQFreeDestination(destinationHandle) );

  /* Create a message handle */

  MQ_ERR_CHK( MQCreateTextMessage(&messageHandle) );

  /* Sending messages */

  index = 0;
  while (1) {

      char text[128];
      printf("Enter a message to send:\n");
      if (fgets(text, 128, stdin) == NULL) {
          break;
      }
      if ((strlen(text) == 0)) {
          break;
      }
      index++;

      /* Set message properties if any */

      MQ_ERR_CHK( MQCreateProperties(&propertiesHandle) );
      MQ_ERR_CHK( MQSetInt32Property(propertiesHandle, "index", index) );
      MQ_ERR_CHK( MQSetMessageProperties(messageHandle, propertiesHandle) );

      /* Set message headers if any */

      MQ_ERR_CHK( MQCreateProperties(&headersHandle) );
      MQ_ERR_CHK( MQSetStringProperty(headersHandle, 
                              MQ_MESSAGE_TYPE_HEADER_PROPERTY, "my-type") );
      MQ_ERR_CHK( MQSetMessageHeaders(messageHandle, headersHandle) );

      /* Set message body */

      MQ_ERR_CHK( MQSetTextMessageText(messageHandle, text) );
     
      printf("Sending message: %s\n", text);
      MQ_ERR_CHK( MQSendMessage(producerHandle, messageHandle) );
  }

  /* Free the message handle */

  MQ_ERR_CHK( MQFreeMessage(messageHandle) );


  MQ_ERR_CHK( MQCreateTextMessage(&messageHandle) );
  MQ_ERR_CHK( MQSetTextMessageText(messageHandle, "END") );
  MQ_ERR_CHK( MQSendMessage(producerHandle, messageHandle) );
  MQ_ERR_CHK( MQFreeMessage(messageHandle) );

  /* Close the message producer */

  MQ_ERR_CHK( MQCloseMessageProducer(producerHandle) );

  /* Close the session */

  MQ_ERR_CHK( MQCloseSession(sessionHandle) );

  /* Close the connection */

  MQ_ERR_CHK( MQCloseConnection(connectionHandle) );

  /* Free the connection handle */

  MQ_ERR_CHK( MQFreeConnection(connectionHandle) );

  return status;

Cleanup:
  { 
  MQString errorString = MQGetStatusString(status);
  fprintf(stderr, "producer(): Error: %s\n",
                  (errorString == NULL) ? "NULL":errorString);
  MQFreeString(errorString);
  }
  MQFreeProperties(propertiesHandle);
  MQFreeProperties(headersHandle);
  MQFreeMessage(messageHandle);
  MQFreeDestination( destinationHandle );
  MQCloseConnection(connectionHandle);
  MQFreeConnection(connectionHandle);

  return status;
}


void
usageExit() {
  fprintf(stderr, "usage: Producer [-h <broker-hostname>] [-p <broker-hostport>]\n");
  fprintf(stderr, "                [-t <topic|queue>] [-d <destination-name>] [-help]\n");
  fprintf(stderr, "\n");
  fprintf(stderr, "       defaults: localhost if no -h\n");
  fprintf(stderr, "                 7676      if no -p\n");
  fprintf(stderr, "                 topic     if no -t\n");
  fprintf(stderr, "                 example_producerconsumer_dest if no -d\n");
  exit(1);
}


int
main(int argc, char *argv[])
{
  char defaultBrokerHost[]      = "localhost";
  int  defaultBrokerPort        = 7676; 
  char defaultDestinationName[] = "example_producerconsumer_dest";

  char    *brokerHost = defaultBrokerHost;
  int      brokerPort = defaultBrokerPort; 
  char    *destinationName = NULL;
  MQDestinationType  destinationType = MQ_TOPIC_DESTINATION;
  int i;


  for (i = 1; i < argc; i++) {

    if (strcmp(argv[i], "-help") == 0) {
      usageExit();
    }
    if (i == argc - 1 || strncmp(argv[i+1], "-", 1) == 0) {
      usageExit();
    }
    if (strcmp(argv[i], "-h") == 0) {
      brokerHost = argv[++i];
      continue;
    }
    if (strcmp(argv[i], "-p") == 0) {
      brokerPort = atoi(argv[++i]);
      continue;
    }
    if (strcmp(argv[i], "-d") == 0) {
      destinationName = argv[++i];
      continue;
    }
    if (strcmp(argv[i], "-t") == 0) {
      if (strncmp(argv[++i], "q", 1) == 0) {
        destinationType = MQ_QUEUE_DESTINATION;
        continue;
      }
      if (strncmp(argv[i], "t", 1) == 0) {
        destinationType = MQ_TOPIC_DESTINATION;
        continue;
      }
      usageExit();
    }
    usageExit();

  }
  if (destinationName == NULL) {
    destinationName = &defaultDestinationName[0];
  }

  if (MQStatusIsError( producer(brokerHost, brokerPort,
                                destinationName, destinationType) )) {
    return 1;
  }

  fprintf(stdout, "Done !\n");
  return 0;
}
