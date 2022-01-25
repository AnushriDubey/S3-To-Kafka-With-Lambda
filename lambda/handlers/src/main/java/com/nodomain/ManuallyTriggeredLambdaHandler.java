package com.nodomain;

import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.HashMap;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;

public class ManuallyTriggeredLambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, List<FailedEvents>>{
    
    private LambdaLogger logger;
    private Utilities utilities;
    private final int MAX_RETRY = 3;
    String dynamoTable = System.getenv("TABLE_NAME");
    private String GET_FAILED_EVENTS_PATH = "/failedEvents/getfailedEvents";
    
    
    @Override
    public List<FailedEvents> handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        
        logger = context.getLogger();
        utilities = new Utilities();
        utilities.logger = logger;
        if(input.getPath().equals(GET_FAILED_EVENTS_PATH)) {
            return retrieveFailedEvents();
        }
        return null;
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
}
