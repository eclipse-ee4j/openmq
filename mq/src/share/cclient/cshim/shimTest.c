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
 * @(#)shimTest.c	1.5 06/26/07
 */ 

#include "shimTest.h"
#include "../debug/DebugUtils.h"
#include "iMQTypes_priv.h"
#include "iMQCallbackTypes_priv.h"
#include "iMQConnectionShim_priv.h"
#include "iMQCallbacks_priv.h"
#include "iMQLogUtilsShim_priv.h"


#include <assert.h>
#include <string.h>
#define ASSERT assert

int g_breakOnErrors = 1;
#define IMQ_ERR_CHK(imqCall)                      \
  if (iMQ_statusIsSuccess(status = (imqCall))) {  \
  } else {                                        \
    if (g_breakOnErrors) {                        \
      BREAKPOINT();                               \
    }                                             \
    goto Cleanup;                                 \
  }

#if defined(WIN32) & !defined(NDEBUG)
# define DUMP_MEMORY_LEAKS(x) dumpMemoryLeaks(x)
#else
# define DUMP_MEMORY_LEAKS(x) ((void)0)
#endif // defined(WIN32)

#if defined(WIN32)
  void dumpMemoryLeaks(const _CrtMemState s1);
#endif /* defined (WIN32) */

#define BREAK_ON_ERROR
#if defined(BREAK_ON_ERROR) 
# if defined(WIN32)
#  define BREAKPOINT() __asm{ int 3 }
# else
#  define BREAKPOINT() *((int*)NULL) = 0 /* core dump */
# endif /* defined(WIN32) */
#else
# define BREAKPOINT() *((int*)NULL) = 
#endif /* BREAK_ON_ERROR */


iMQStatus shimPropertiesTest();
iMQStatus shimMessageTest();
iMQStatus shimConnectionTest();
iMQStatus shimSessionTest(const iMQConnectionHandle connectionHandle);
iMQStatus shimProducerTest(const iMQSessionHandle sessionHandle);
iMQStatus shimConsumerTest(const iMQSessionHandle sessionHandle,
                           const iMQConnectionHandle connectionHandle);


// Callback declarations
#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */
  void messageArrivedFunc(const iMQSessionHandle sessionHandle,
                          const iMQConsumerHandle consumerHandle,
                          void* callbackData);
  void exceptionListenerFunc(const iMQConnectionHandle  connectionHandle,
                             const iMQStatus exception,
                             void * callbackData );
  iMQBool createThreadFunc(iMQThreadFunc startFunc,
                           void * arg,     
                           void * callbackData);
#ifdef __cplusplus
}
#endif /* __cplusplus */
/*
 *
 */
iMQStatus
shimTest()
{
  int i = 0;
  int j = 0;
  int totalAllocations = 0;
  int allocationsBetweenFailures = 10;
#ifdef WIN32
  _CrtMemState s1;
#endif
  iMQStatus status;
  //iMQ_setLogFilePrefix("c:/temp/imq_test");
  iMQ_setLogFilePrefix("/home/de134463/tmp/imq_test/");
  //iMQ_setLogFileLogLevel(iMQFinestLevel);
  //iMQ_setLogFileLogLevel(iMQInfoLevel);
  //iMQ_setLogFileLogLevel(iMQWarningLevel);
  //iMQ_setStdErrLogLevel(iMQWarningLevel);
  iMQ_setLogFileLogLevel(iMQFinestLevel);
  iMQ_setStdErrLogLevel(iMQWarningLevel);
  //iMQ_initialzeSSL("c:/ssl-dir/certdb", "DOMESTIC");

#ifdef WIN32
  _CrtMemCheckpoint( &s1 );
#endif

  // Run the tests and see how many allocations were needed
  //_CrtSetBreakAlloc(5588);
  //_CrtSetBreakAlloc(22131);

  // Test Properties
  g_breakOnErrors = 1;
  setAllocationsBeforeFailure(0);
  IMQ_ERR_CHK( shimPropertiesTest() );
  DUMP_MEMORY_LEAKS(s1);
  totalAllocations = getCurrentAllocation();
  fprintf(stderr, "shimPropertiesTest() used %d allocations\n", totalAllocations);
  g_breakOnErrors = 0;
  for (i = 1; i <= totalAllocations; i++ ) {
    setAllocationsBeforeFailure(i);
    shimPropertiesTest();
    DUMP_MEMORY_LEAKS(s1);
  }

  // Test Message
  g_breakOnErrors = 1;
  setAllocationsBeforeFailure(0);
  IMQ_ERR_CHK( shimMessageTest() );
  DUMP_MEMORY_LEAKS(s1);
  totalAllocations = getCurrentAllocation();
  fprintf(stderr, "shimMessageTest() used %d allocations\n", totalAllocations);
  g_breakOnErrors = 0;
  for (i = 1; i <= totalAllocations; i++ ) {
    setAllocationsBeforeFailure(i);
    shimMessageTest();
    DUMP_MEMORY_LEAKS(s1);
  }
  
  // Test Connection
  g_breakOnErrors = 1;
  setAllocationsBeforeFailure(0);
  IMQ_ERR_CHK( shimConnectionTest() );
  DUMP_MEMORY_LEAKS(s1);
  totalAllocations = getCurrentAllocation();
  fprintf(stderr, "shimConnectionTest() used %d allocations\n", totalAllocations);
  g_breakOnErrors = 0;
  //for (i = 1; i <= totalAllocations; i++ ) {
  //for (i = 1817; i <= totalAllocations; i++ ) {
  for (j = 0; j < 10000; j++) {
    //for (i = 2596; i <= 2596; i++ ) {
    //fprintf(stderr, "\n\n---------------------------------------------------------------\n");
    //fprintf(stderr, "j = %d\n\n", j);
    allocationsBetweenFailures = 1;
    //for (i = 1; i <= totalAllocations; i += allocationsBetweenFailures) {
    //for (i = 1000; i <= totalAllocations; i += allocationsBetweenFailures) {
    //for (i = 2273; i <= totalAllocations; i += allocationsBetweenFailures) {
    // need to test the broker failure with i = 1 to 2500.
    for (i = 1; i <= totalAllocations; i += allocationsBetweenFailures) {
    //for (i = 160; i <= 175; i++ ) {
      fprintf(stderr, "%d\n", i);
      setAllocationFailuresContinue(PR_TRUE);
      setAllocationsBeforeFailure(i);
      //setAllocationsBeforeBrokerFailure(i);
      shimConnectionTest();
      DUMP_MEMORY_LEAKS(s1);
    }
  }

  return status;
Cleanup:
  DUMP_MEMORY_LEAKS(s1);
  BREAKPOINT();
  return status;
}

/*
 *
 */
iMQStatus
shimPropertiesTest()
{
  iMQPropertiesHandle handle = INVALID_IMQ_HANDLE;
  iMQType propType;
  iMQStatus status;

  ConstIMQString    valueString;
  iMQBool           valueBool;
  iMQInt8           valueInt8;
  iMQInt16          valueInt16;
  iMQInt32          valueInt32;
  iMQInt64          valueInt64;
  iMQFloat32        valueFloat32;
  iMQFloat64        valueFloat64;
  
  iMQInt32          hi32, lo32;

  // Test create and free
  IMQ_ERR_CHK( iMQ_createProperties(&handle) );
  IMQ_ERR_CHK( iMQ_freeProperties(handle) );
    
  // Test iterating and get/set
  IMQ_ERR_CHK( iMQ_createProperties(&handle) );
  IMQ_ERR_CHK( iMQ_propertiesKeyIterationStart(handle) );

  // String set/get/gettype
  IMQ_ERR_CHK( iMQ_setStringProperty("SomeStringProp1", "String Value", handle) );  
  IMQ_ERR_CHK( iMQ_getStringProperty("SomeStringProp1", &valueString, handle) );
  ASSERT( strcmp(valueString, "String Value") == 0 );
  IMQ_ERR_CHK( iMQ_getPropertyType("SomeStringProp1", &propType, handle) );
  ASSERT( propType == iMQStringType );

  // Bool set/get/gettype
  IMQ_ERR_CHK( iMQ_setBoolProperty("SomeBoolProp1", IMQ_TRUE, handle) );
  IMQ_ERR_CHK( iMQ_getBoolProperty("SomeBoolProp1", &valueBool, handle) );   
  ASSERT( valueBool == IMQ_TRUE );
  IMQ_ERR_CHK( iMQ_getPropertyType("SomeBoolProp1", &propType, handle) );
  ASSERT( propType == iMQBoolType );

  // Int8 set/get/gettype
  IMQ_ERR_CHK( iMQ_setInt8Property("SomeInt8Prop1", 1, handle) );
  IMQ_ERR_CHK( iMQ_getInt8Property("SomeInt8Prop1", &valueInt8, handle) );
  ASSERT( valueInt8 == 1 );
  IMQ_ERR_CHK( iMQ_getPropertyType("SomeInt8Prop1", &propType, handle) );
  ASSERT( propType == iMQInt8Type );

  // Int16 set/get/gettype
  IMQ_ERR_CHK( iMQ_setInt16Property("SomeInt16Prop1", 256, handle) );
  IMQ_ERR_CHK( iMQ_getInt16Property("SomeInt16Prop1", &valueInt16, handle) );
  ASSERT( valueInt16 == 256 );
  IMQ_ERR_CHK( iMQ_getPropertyType("SomeInt16Prop1", &propType, handle) );
  ASSERT( propType == iMQInt16Type );

  // Int32 set/get/gettype
  IMQ_ERR_CHK( iMQ_setInt32Property("SomeInt32Prop1", 70000, handle) );
  IMQ_ERR_CHK( iMQ_getInt32Property("SomeInt32Prop1", &valueInt32, handle) );
  ASSERT( valueInt32 == 70000 );
  IMQ_ERR_CHK( iMQ_getPropertyType("SomeInt32Prop1", &propType, handle) );
  ASSERT( propType == iMQInt32Type );

  // Int64 set/get/gettype
  valueInt64 = int64FromInt32Parts(0x7FFFFFFF, 0xFFFFFFFF);
  IMQ_ERR_CHK( iMQ_setInt64Property("SomeInt64Prop1", valueInt64, handle) );
  IMQ_ERR_CHK( iMQ_getInt64Property("SomeInt64Prop1", &valueInt64, handle) );  
  int32PartsFromInt64(&hi32, &lo32, valueInt64);
  ASSERT( (hi32 == 0x7FFFFFFF) && (lo32 == 0xFFFFFFFF) );
  IMQ_ERR_CHK( iMQ_getPropertyType("SomeInt64Prop1", &propType, handle) );
  ASSERT( propType == iMQInt64Type );

  // Float32 set/get/gettype
  IMQ_ERR_CHK( iMQ_setFloat32Property("SomeFloat32Prop1", 3.5, handle) );
  IMQ_ERR_CHK( iMQ_getFloat32Property("SomeFloat32Prop1", &valueFloat32, handle) );
  ASSERT( valueFloat32 == 3.5 );
  IMQ_ERR_CHK( iMQ_getPropertyType("SomeFloat32Prop1", &propType, handle) );
  ASSERT( propType == iMQFloat32Type );
    
  // Float64 set/get/gettype
  IMQ_ERR_CHK( iMQ_setFloat64Property("SomeFloat64Prop1", 3.5e300, handle) );
  IMQ_ERR_CHK( iMQ_getFloat64Property("SomeFloat64Prop1", &valueFloat64, handle) );
  ASSERT( valueFloat64 == 3.5e300 );
  IMQ_ERR_CHK( iMQ_getPropertyType("SomeFloat64Prop1", &propType, handle) );
  ASSERT( propType == iMQFloat64Type );
    
  IMQ_ERR_CHK( iMQ_freeProperties(handle) );
  
  return status;
Cleanup:
  iMQ_freeProperties(handle);
  return status;
}



/*
 *
 */
#define NUM_MESSAGE_HANDLES 2
iMQStatus
shimMessageTest()
{
  ConstIMQString messageBody;
  const iMQInt8 * messageBodyBytes;
  iMQInt32 messageBodyBytesSize;
  iMQPropertiesHandle propertiesHandle = INVALID_IMQ_HANDLE;
  iMQPropertiesHandle headersHandle = INVALID_IMQ_HANDLE;
  iMQMessageHandle messageHandle = INVALID_IMQ_HANDLE;
  iMQMessageHandle messageHandles[NUM_MESSAGE_HANDLES];
  iMQStatus status;
  iMQMessageType messageType;
  int i;

  // Initialize the message handles
  for (i = 0; i < NUM_MESSAGE_HANDLES; i++) {
    messageHandles[i] = messageHandle;
  }
  
  // Create a text message and a bytes message
  IMQ_ERR_CHK( iMQ_createTextMessage(&(messageHandles[0])) );
  IMQ_ERR_CHK( iMQ_createBytesMessage(&(messageHandles[1])) );

  for (i = 0; i < NUM_MESSAGE_HANDLES; i++) {
    messageHandle = messageHandles[i];

    // Create some bogus properties 
    IMQ_ERR_CHK( iMQ_createProperties(&propertiesHandle) );
    IMQ_ERR_CHK( iMQ_setStringProperty("Property1", "grouse", propertiesHandle) );
    IMQ_ERR_CHK( iMQ_setInt32Property("Property2", 7676, propertiesHandle) );
    IMQ_ERR_CHK( iMQ_setBoolProperty("Property3", IMQ_TRUE, propertiesHandle) );
    IMQ_ERR_CHK( iMQ_setBoolProperty("Property4", IMQ_FALSE, propertiesHandle) );
    IMQ_ERR_CHK( iMQ_setBoolProperty("Property5", PR_TRUE, propertiesHandle) );
    IMQ_ERR_CHK( iMQ_setInt32Property("Property6", 100, propertiesHandle) );
    IMQ_ERR_CHK( iMQ_setInt32Property("Property7", 50, propertiesHandle) );

    // Set the message properties
    IMQ_ERR_CHK( iMQ_setMessageProperties(messageHandle, propertiesHandle) );
    ASSERT( iMQ_statusIsError(iMQ_freeProperties(propertiesHandle)) ); // shouldn't be able to free them

    // Get the message properties and free them
    IMQ_ERR_CHK( iMQ_getMessageProperties(messageHandle, &propertiesHandle) );
    IMQ_ERR_CHK( iMQ_freeProperties(propertiesHandle) );

    // Get the message headers and free them
    IMQ_ERR_CHK( iMQ_getMessageHeaders(messageHandle, &headersHandle) );
    IMQ_ERR_CHK( iMQ_freeProperties(headersHandle) );

    // Set some message headers, and set them to the packet
    IMQ_ERR_CHK( iMQ_createProperties(&headersHandle) );
    IMQ_ERR_CHK( iMQ_setStringProperty(IMQ_CORRELATION_ID_HEADER_PROPERTY, 
                                       "SomeCorrelationID", headersHandle) );
    IMQ_ERR_CHK( iMQ_setBoolProperty(IMQ_PERSISTENT_HEADER_PROPERTY, 
                                     PR_TRUE, headersHandle) );
    IMQ_ERR_CHK( iMQ_setInt16Property(IMQ_MESSAGE_TYPE_HEADER_PROPERTY,
                                      23, headersHandle) );
    IMQ_ERR_CHK( iMQ_setMessageHeaders(messageHandle, headersHandle) );
    ASSERT( iMQ_statusIsError(iMQ_freeProperties(headersHandle)) ); // shouldn't be able to free them

    // Get the message headers and free them
    IMQ_ERR_CHK( iMQ_getMessageHeaders(messageHandle, &headersHandle) );
    IMQ_ERR_CHK( iMQ_freeProperties(headersHandle) );

    IMQ_ERR_CHK( iMQ_getMessageType(messageHandle, &messageType) );

    if (messageType == iMQTextMessageType) {
      // Get the message body 
      IMQ_ERR_CHK( iMQ_getTextMessageText(messageHandle, &messageBody) );

      // Set the body and then get the message body 
      IMQ_ERR_CHK( iMQ_setTextMessageText(messageHandle, "This is the message body") );
      IMQ_ERR_CHK( iMQ_getTextMessageText(messageHandle, &messageBody) );
    } else if (messageType == iMQBytesMessageType) {
      // Get the message body 
      IMQ_ERR_CHK( iMQ_getBytesMessageBytes(messageHandle, &messageBodyBytes, &messageBodyBytesSize) );

      // Set the body and then get the message body 
      IMQ_ERR_CHK( iMQ_setBytesMessageBytes(messageHandle,
                                            (const iMQInt8*)"This is the message body", (iMQInt32)strlen("This is the message body")) );
      IMQ_ERR_CHK( iMQ_getBytesMessageBytes(messageHandle, &messageBodyBytes, &messageBodyBytesSize) );
    }
    
    // Free the message
    IMQ_ERR_CHK( iMQ_freeMessage(messageHandle) );
  }
  
  return status;
Cleanup:
  for (i = 0; i < NUM_MESSAGE_HANDLES; i++) {
   iMQ_freeMessage(messageHandles[i]);
  }
  iMQ_freeProperties(propertiesHandle);
  iMQ_freeProperties(headersHandle);
  
  return status;
}


/*
 *
 */
iMQStatus
shimConnectionTest()
{
  iMQStatus status;
  iMQPropertiesHandle propertiesHandle = INVALID_IMQ_HANDLE;
  iMQConnectionHandle connectionHandle = INVALID_IMQ_HANDLE;

  // Set up the connection properties
  IMQ_ERR_CHK( iMQ_createProperties(&propertiesHandle) );
  IMQ_ERR_CHK( iMQ_setStringProperty(IMQ_BROKER_NAME_PROPERTY, "grouse", propertiesHandle) );  // or "chod"
  IMQ_ERR_CHK( iMQ_setInt32Property(IMQ_BROKER_PORT_PROPERTY, 7676, propertiesHandle) );

  IMQ_ERR_CHK( iMQ_setStringProperty(IMQ_CONNECTION_TYPE_PROPERTY, "TCP", propertiesHandle) );
//  IMQ_ERR_CHK( iMQ_setBoolProperty(IMQ_ACK_ON_PRODUCE_PROPERTY, IMQ_TRUE, propertiesHandle) );
//  IMQ_ERR_CHK( iMQ_setBoolProperty(IMQ_ACK_ON_ACKNOWLEDGE_PROPERTY, IMQ_FALSE, propertiesHandle) );
//  IMQ_ERR_CHK( iMQ_setBoolProperty(IMQ_FLOW_CONTROL_IS_LIMITED_PROPERTY, PR_TRUE, propertiesHandle) );
//  IMQ_ERR_CHK( iMQ_setInt32Property(IMQ_FLOW_CONTROL_LIMIT_PROPERTY, 100, propertiesHandle) );
//  IMQ_ERR_CHK( iMQ_setInt32Property(IMQ_FLOW_CONTROL_COUNT_PROPERTY, 50, propertiesHandle) );

  // Open the connection
  //IMQ_ERR_CHK( iMQ_createConnection(propertiesHandle, "guest", "guest", NULL, NULL, &connectionHandle);
  IMQ_ERR_CHK( iMQ_createConnection(propertiesHandle, "guest", "guest", createThreadFunc, NULL, &connectionHandle) );
  ASSERT( iMQ_statusIsError(iMQ_freeProperties(propertiesHandle)) ); // we shouldn't be able access properties now

  // Test the session
  IMQ_ERR_CHK( shimSessionTest(connectionHandle) );

  // Close the connection
  IMQ_ERR_CHK( iMQ_closeConnection(connectionHandle) );

  fprintf(stderr, "Calling iMQ_deleteConnection\n");
  IMQ_ERR_CHK( iMQ_deleteConnection(connectionHandle) );

  return status;
Cleanup:
  iMQ_freeProperties(propertiesHandle);
  iMQ_closeConnection(connectionHandle);

  fprintf(stderr, "Calling iMQ_deleteConnection\n");
  iMQ_deleteConnection(connectionHandle);

  return status;
}



iMQStatus
shimSessionTest(const iMQConnectionHandle connectionHandle)
{
  iMQStatus status;
  iMQSessionHandle sessionHandle = INVALID_IMQ_HANDLE;
  iMQDestinationHandle destinationHandle = INVALID_IMQ_HANDLE;
  
  // Create a session
  IMQ_ERR_CHK( iMQ_createSession(connectionHandle, IMQ_FALSE, IMQ_CLIENT_ACKNOWLEDGE, &sessionHandle) );

  // Create a destination and free it
  IMQ_ERR_CHK( iMQ_createDestination(sessionHandle, "SomeTopic", IMQ_FALSE, &destinationHandle) );
  IMQ_ERR_CHK( iMQ_freeDestination(destinationHandle) );

  // Create a temporary destination and free it
  IMQ_ERR_CHK( iMQ_createTemporaryDestination(sessionHandle, IMQ_FALSE, &destinationHandle) );
  IMQ_ERR_CHK( iMQ_freeDestination(destinationHandle) );

  // Test the producer
  IMQ_ERR_CHK( shimProducerTest(sessionHandle) );
  
  // Test the consumer
  IMQ_ERR_CHK( shimConsumerTest(sessionHandle, connectionHandle) );

  // Start the connection
  IMQ_ERR_CHK( iMQ_startConnection(connectionHandle) );

  // Stop the connection
  IMQ_ERR_CHK( iMQ_stopConnection(connectionHandle) );

  // Close the session
  IMQ_ERR_CHK( iMQ_closeSession(sessionHandle) );
  ASSERT( iMQ_statusIsError(iMQ_closeSession(sessionHandle)) ); // we shouldn't be able access session now
  
  return status;
Cleanup:
  iMQ_freeDestination(destinationHandle);
  iMQ_closeSession(sessionHandle);
  return status;
}


/*
 *
 */
iMQStatus shimProducerTest(const iMQSessionHandle sessionHandle)
{
  iMQStatus status;
  iMQDestinationHandle destinationHandle = INVALID_IMQ_HANDLE;
  iMQProducerHandle producerHandle = INVALID_IMQ_HANDLE;
  iMQMessageHandle messageHandle = INVALID_IMQ_HANDLE;

//
// Test a producer WITHOUT a default destination
//
  // Create a producer without a destination
  IMQ_ERR_CHK( iMQ_createMessageProducer(sessionHandle, &producerHandle) );

  // Create a destination for the producer
  IMQ_ERR_CHK( iMQ_createDestination(sessionHandle, "SomeTopic", IMQ_FALSE, &destinationHandle) );

  // Create a message for the destination and
  IMQ_ERR_CHK( iMQ_createTextMessage(&messageHandle) );

  // Send the message
  IMQ_ERR_CHK( iMQ_setTextMessageText(messageHandle, "This is the first message") );
  IMQ_ERR_CHK( iMQ_sendMessageTo(producerHandle, messageHandle, destinationHandle) );

  // Send the message and override some default parameters
  IMQ_ERR_CHK( iMQ_setTextMessageText(messageHandle, "This is the second message") );
  IMQ_ERR_CHK( iMQ_sendMessageToEx(producerHandle, messageHandle, destinationHandle,
                                   IMQ_PERSISTENT_DELIVERY/*IMQ_NON_PERSISTENT_DELIVERY*/, 5, 0) );

  // Close the Destination, Producer and Message
  IMQ_ERR_CHK( iMQ_closeMessageProducer(producerHandle) );
  IMQ_ERR_CHK( iMQ_freeDestination(destinationHandle) );
  IMQ_ERR_CHK( iMQ_freeMessage(messageHandle) );

//
// Test a producer WITH a default destination
//
  // Create a destination and a producer for it
  IMQ_ERR_CHK( iMQ_createDestination(sessionHandle, "SomeTopic", IMQ_FALSE, &destinationHandle) );
  IMQ_ERR_CHK( iMQ_createMessageProducerWithDestination(sessionHandle, destinationHandle, &producerHandle) );
  ASSERT( iMQ_statusIsError(iMQ_freeDestination(destinationHandle)) );   // we shouldn't be able to access destinationHandle now

  // Create a message for the destination and
  IMQ_ERR_CHK( iMQ_createTextMessage(&messageHandle) );
  IMQ_ERR_CHK( iMQ_setTextMessageText(messageHandle, "This is the message body1") );

  // Send the message
  IMQ_ERR_CHK( iMQ_sendMessage(producerHandle, messageHandle) );

  // Send the message and override some default parameters
  IMQ_ERR_CHK( iMQ_sendMessageEx(producerHandle, messageHandle, IMQ_NON_PERSISTENT_DELIVERY, 5, 0) );

  // Free the message and close the producer
  IMQ_ERR_CHK( iMQ_freeMessage(messageHandle) );
  IMQ_ERR_CHK( iMQ_closeMessageProducer(producerHandle) );

  return status;
Cleanup:
  iMQ_freeDestination( destinationHandle );
  iMQ_freeMessage( messageHandle );
  iMQ_closeMessageProducer( producerHandle );
  return status;
}

/*
 *
 */
iMQStatus shimConsumerTest(const iMQSessionHandle sessionHandle,
                           const iMQConnectionHandle connectionHandle)
{
  iMQStatus status;
  iMQDestinationHandle destinationHandle = INVALID_IMQ_HANDLE;
  iMQDestinationHandle producerReplyToHandle = INVALID_IMQ_HANDLE;
  iMQDestinationHandle consumerReplyToHandle = INVALID_IMQ_HANDLE;
  iMQConsumerHandle consumerHandle = INVALID_IMQ_HANDLE;
  iMQProducerHandle producerHandle = INVALID_IMQ_HANDLE;
  iMQMessageHandle messageHandle = INVALID_IMQ_HANDLE;

  // Create a destination and a consumer for it, and close the consumer
  IMQ_ERR_CHK( iMQ_createDestination(sessionHandle, "SomeTopic", IMQ_FALSE, 
                                     &destinationHandle) );
  IMQ_ERR_CHK( iMQ_createMessageConsumer(sessionHandle, destinationHandle, IMQ_TRUE, &consumerHandle) );
  ASSERT( iMQ_statusIsError( iMQ_freeDestination(destinationHandle)) );   // we shouldn't be able to access destinationHandle now
  IMQ_ERR_CHK( iMQ_closeMessageConsumer(consumerHandle) );

  // Create a destination and a durable consumer for it, close the consumer, and unsubscribe it
  IMQ_ERR_CHK( iMQ_createDestination(sessionHandle, "SomeTopic2", IMQ_FALSE, 
                                     &destinationHandle) );
  IMQ_ERR_CHK( iMQ_createDurableMessageConsumer(sessionHandle, destinationHandle, 
                                                "durableNameIsTom", IMQ_TRUE, &consumerHandle) );
  ASSERT( iMQ_statusIsError(iMQ_freeDestination(destinationHandle)) );   // we shouldn't be able to access destinationHandle now
  ASSERT( iMQ_statusIsError(iMQ_unsubscribeDurableMessageConsumer(sessionHandle, "durableNameIsTom")) ); // can't unsubscribe active subscriber
  IMQ_ERR_CHK( iMQ_closeMessageConsumer(consumerHandle) );
  IMQ_ERR_CHK( iMQ_unsubscribeDurableMessageConsumer(sessionHandle, "durableNameIsTom") );

  // Create a destination and a consumer for it
  IMQ_ERR_CHK( iMQ_createDestination(sessionHandle, "SomeTopic", IMQ_FALSE, 
                                     &destinationHandle) );
  IMQ_ERR_CHK( iMQ_createMessageConsumer(sessionHandle, destinationHandle, IMQ_FALSE, &consumerHandle) );

  // Set the message arrived callback
  IMQ_ERR_CHK( iMQ_setMessageArrivedFunc(consumerHandle, messageArrivedFunc, NULL) );

  // Start the connection
  IMQ_ERR_CHK( iMQ_startConnection(connectionHandle) );

  // Send Create a destination, a temporary reply destination, and a producer for it
  IMQ_ERR_CHK( iMQ_createDestination(sessionHandle, "SomeTopic", IMQ_FALSE, 
                                     &destinationHandle) );
  IMQ_ERR_CHK( iMQ_createTemporaryDestination(sessionHandle, IMQ_FALSE, &producerReplyToHandle) );
  IMQ_ERR_CHK( iMQ_createMessageProducerWithDestination(sessionHandle, destinationHandle, &producerHandle) );
  ASSERT( iMQ_statusIsError( iMQ_freeDestination(destinationHandle)) );   // we shouldn't be able to access destinationHandle now

  // Create a message for the destination and send it 
  IMQ_ERR_CHK( iMQ_createTextMessage(&messageHandle) );
  IMQ_ERR_CHK( iMQ_setTextMessageText(messageHandle, "This is the message body1") );
  IMQ_ERR_CHK( iMQ_setMessageReplyTo(messageHandle, producerReplyToHandle) );
  //secIMQ_ERR_CHK( iMQ_sendMessage(producerHandle, messageHandle) );

  IMQ_ERR_CHK( iMQ_sendMessageEx(producerHandle, messageHandle, 
                                 IMQ_PERSISTENT_DELIVERY/*IMQ_NON_PERSISTENT_DELIVERY*/, 5, 0) );


//IMQ_PERSISTENT_DELIVERY

  // Close the Destination, Producer and Message
  IMQ_ERR_CHK( iMQ_closeMessageProducer(producerHandle) );
  IMQ_ERR_CHK( iMQ_freeMessage(messageHandle) );

  // Try to receive 3 messages (only the first receive will work)
  IMQ_ERR_CHK( iMQ_receiveMessageWait(consumerHandle, &messageHandle) );
  IMQ_ERR_CHK( iMQ_getMessageReplyTo(messageHandle, &consumerReplyToHandle) );
  IMQ_ERR_CHK( iMQ_acknowledgeMessage(consumerHandle, messageHandle) );
  IMQ_ERR_CHK( iMQ_freeMessage(messageHandle) );
  //ASSERT( iMQ_getStatusCode( iMQ_receiveMessageNoWait(consumerHandle, &messageHandle) ) == IMQ_NO_MESSAGE );
            
  //ASSERT( iMQ_getStatusCode(iMQ_receiveMessageWithTimeout(consumerHandle, 1 * 1000 * 1000, &messageHandle)) == IMQ_TIMEOUT_EXPIRED ); // wait for 1 second

  // close the consumer
  IMQ_ERR_CHK( iMQ_closeMessageConsumer(consumerHandle) );

  // close the two replyto handles
  IMQ_ERR_CHK( iMQ_freeDestination(producerReplyToHandle) );
  IMQ_ERR_CHK( iMQ_freeDestination(consumerReplyToHandle) );

  return status;
Cleanup:
  iMQ_freeDestination( destinationHandle );
  iMQ_freeDestination( producerReplyToHandle );
  iMQ_freeDestination( consumerReplyToHandle );
  iMQ_closeMessageConsumer( consumerHandle );
  iMQ_closeMessageProducer( producerHandle );
  iMQ_freeMessage( messageHandle );
  return status;
}

// ------------------------------------------------
// Callbacks

/*
 *
 */
void
messageArrivedFunc(const iMQSessionHandle sessionHandle,
                   const iMQConsumerHandle consumerHandle,
                   void* callbackData)
{
  // do nothing
  ((void)sessionHandle);
  ((void)consumerHandle);
  ((void)callbackData);
}

/*
 *
 */
void 
exceptionListenerFunc(const iMQConnectionHandle  connectionHandle,
                      const iMQStatus exception,
                      void * callbackData)
{
  // do nothing
  ((void)connectionHandle);
  ((void)exception);
  ((void)callbackData);
}

/*
 *
 */
#include <nspr.h>
iMQBool 
createThreadFunc(iMQThreadFunc startFunc,
                 void * arg,     
                 void * callbackData)
{
  PRThread * thread = NULL;

  ((void)callbackData);
 
  thread = PR_CreateThread(PR_SYSTEM_THREAD, 
                           startFunc, 
                           arg, 
                           PR_PRIORITY_NORMAL, 
                           PR_GLOBAL_THREAD, 
                           PR_UNJOINABLE_THREAD, 
                           0); 

  return (thread != NULL);
}

#if defined(WIN32)
  void
  dumpMemoryLeaks(const _CrtMemState s1)
  {
    _CrtMemState s2, s3;
    if (!_CrtCheckMemory()) {
      fprintf(stderr, "Warning: memory corrupted.\n");
      BREAKPOINT();
    }

    _CrtMemCheckpoint( &s2 );
    if ( _CrtMemDifference( &s3, &s1, &s2) ) {
      fprintf(stderr, "Warning: detected memory leaks.\n");
      _CrtMemDumpStatistics( &s3 );
      _CrtDumpMemoryLeaks();
      BREAKPOINT();
    }
  }
#endif // defined(WIN32)

int 
main()
{
  shimTest();
  return 0;
}




