package com.nodomain;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Map;
import java.util.HashMap;

import java.util.List;
import java.util.Date;

import org.json.JSONObject;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LambdaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

public class CronScheduledLambdaHandler implements RequestHandler<ScheduledEvent, String> {

    private LambdaLogger logger;
    private final int MAX_RETRY = 3;
    private final Region region = Region.US_EAST_2;
    private Utilities utilities;


    public String handleRequest(ScheduledEvent scheduledEvent, Context context) {

        logger = context.getLogger();
        logger.log(scheduledEvent.toString());
        utilities = new Utilities();
        utilities.logger = logger;
        

        List<FailedEvents> failedEvents = retrieveFailedEvents();

        // failed to update one evnet in for loop
        try (LambdaClient awsLambda = LambdaClient.builder().region(region).build()) {
            for(FailedEvents failedEvent: failedEvents) {
        
                SQSEvent eventJson = mapFailedEventToSqsEvent(failedEvent.getFailedEvent());

                JSONObject eventBodyContent = utilities.extractEventBodyJsonObject(eventJson);

                String failedFunctionArn = extractFailedFunctionArString(eventBodyContent);
            
                JSONObject requestPayloadJsonObject = utilities.extractRequestPayload(eventBodyContent);

                String requestId = utilities.amazonRequestId(requestPayloadJsonObject);
                logger.log("requestId - \n" + requestId);
                InvokeResponse  invokeResponse = invokeFunction(awsLambda, failedFunctionArn, requestPayloadJsonObject.toString());
                logger.log("Lambda invokation completed");
                
                Integer statusCode = invokeResponse.statusCode();
                if(statusCode == 202) {
                    utilities.updateFailedEventsTable(failedEvent, requestId);
                }
            }
         }
        return "OK";
    }


    public List<FailedEvents> retrieveFailedEvents() {
        String filterString = "eventStatus = :STATUS and " +
            "retryCount < :MAX_RETRY and " + 
            "nextRunTimeStamp <= :CURRENT_TIMESTAMP ";
        
        Map<String, AttributeValue> filterValues = new HashMap<String, AttributeValue>();
        filterValues.put(":STATUS", new AttributeValue().withS("PENDING"));
        filterValues.put(":MAX_RETRY", new AttributeValue().withN(Integer.toString(MAX_RETRY)));
        filterValues.put(":CURRENT_TIMESTAMP", new AttributeValue().withN(Long.toString(new Date().getTime())));

        return utilities.getFailedEventsToRetry(MAX_RETRY, filterString, filterValues);
    }
    
    public String extractFailedFunctionArString(JSONObject bodyJsonObject) {
        JSONObject requestContextJsonObject = bodyJsonObject.getJSONObject("requestContext");
        String functionArn = requestContextJsonObject.getString("functionArn").replace(":$LATEST", "");
        return functionArn;
    }


    public SQSEvent mapFailedEventToSqsEvent(String failedEvent) {
        ObjectMapper objectMapper = new ObjectMapper();
        SQSEvent eventJson = null;
            try{
                eventJson = objectMapper.readValue(failedEvent, SQSEvent.class);
            }
            catch(JsonProcessingException ex) {
                throw new RuntimeException("Failed to map failed event to SQSEvent.");
            }
            return eventJson;
    }

    public InvokeResponse invokeFunction(LambdaClient awsLambda, String functionName, String requestEventString) {
        InvokeResponse response = null ;
        try {
           SdkBytes payload = SdkBytes.fromUtf8String(requestEventString) ;

           //Setup an InvokeRequest
           InvokeRequest request = InvokeRequest.builder()
                   .functionName(functionName)
                   .invocationType(InvocationType.EVENT)
                   .payload(payload)
                   .build();

           response = awsLambda.invoke(request);
           logger.log(response.toString());
       } catch(LambdaException e) {
           logger.log(e.getMessage());
           throw e;
       }
       return response;
   }
}
