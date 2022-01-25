package com.nodomain;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName = "FailedEventsSummary")
public class FailedEvents {

    
    String requestId;
    String failedEvent;
    String eventStatus;
    String timeStamp;
    Integer retryCount;
    String nextRunTimeStamp;

    @DynamoDBAttribute
    public String getTimeStamp() {
        return timeStamp;
    }
    public void setTimeStamp(String timeStamp) {
        this.timeStamp = timeStamp;
    }

    @DynamoDBAttribute
    public Integer getRetryCount() {
        return retryCount;
    }
    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }
    @DynamoDBHashKey
    public String getRequestId() {
        return requestId;
    }
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    
    @DynamoDBAttribute
    public String getFailedEvent() {
        return failedEvent;
    }
    public void setFailedEvent(String failedEvent) {
        this.failedEvent = failedEvent;
    }

    @DynamoDBAttribute
    public String getEventStatus() {
        return eventStatus;
    }
    public void setEventStatus(String eventStatus) {
        this.eventStatus = eventStatus;
    }

    @DynamoDBAttribute
    public String getNextRunTimeStamp() {
        return nextRunTimeStamp;
    }
    public void setNextRunTimeStamp(String nextRunTimeStamp) {
        this.nextRunTimeStamp = nextRunTimeStamp;
    }
}

