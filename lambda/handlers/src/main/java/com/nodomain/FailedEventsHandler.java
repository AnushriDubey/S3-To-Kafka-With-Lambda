package com.nodomain;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.json.JSONObject;

public class FailedEventsHandler implements RequestHandler<SQSEvent, String> {

    private LambdaLogger logger;
    //private final int MAX_RETRY = 3;
    private Utilities utilities;

    public String handleRequest(SQSEvent sqsEvent, Context context) {

        logger = context.getLogger();
        logger.log(sqsEvent.toString());
        utilities = new Utilities();
        utilities.logger = logger;

        ObjectMapper objectMapper = new ObjectMapper();
        String sqsEventString = null;
        try{
            sqsEventString = objectMapper.writeValueAsString(sqsEvent);
        }
        catch(JsonProcessingException ex) {

        }
        String failedReqestId = extractRequestId(sqsEvent);
        logger.log(sqsEventString);
        utilities.addElementInFailedEventsTable(sqsEventString, failedReqestId);
        return "OK";
    }

    public String extractRequestId(SQSEvent sqsEvent) {
        JSONObject eventBodyContent = utilities.extractEventBodyJsonObject(sqsEvent);
        JSONObject requestPayloadJsonObject = utilities.extractRequestPayload(eventBodyContent);
        return utilities.amazonRequestId(requestPayloadJsonObject);
    }
    
}
