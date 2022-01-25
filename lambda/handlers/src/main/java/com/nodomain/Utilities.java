package com.nodomain;

import java.util.Map;
import java.util.List;
import java.util.Date;
import java.time.Instant;
import java.time.*;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.UpdateItemOutcome;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import org.json.JSONObject;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.s3.model.CopyObjectResult;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.SdkClientException;

public class Utilities {

    protected LambdaLogger logger;
    protected AmazonDynamoDB dynamoDBClient;
    protected DynamoDB dynamoDb;
    protected DynamoDBMapper mapper;
    protected Table table;

    private final int MAX_RETRY = 4;
    private final String failedEventsTableName = "FailedEventsSummary";
    public static Map<Integer, Duration> retryIntervalMap = Map.of(0, Duration.ofMinutes(10),
            1, Duration.ofHours(1),
            2, Duration.ofHours(3),
            3, Duration.ofHours(10),
            4, Duration.ofHours(20));

    Utilities() {
        dynamoDBClient = AmazonDynamoDBClientBuilder.standard().build();
        dynamoDb = new DynamoDB(dynamoDBClient);
        table = dynamoDb.getTable(failedEventsTableName);
        mapper = new DynamoDBMapper(dynamoDBClient);
    }

    public JSONObject extractEventBodyJsonObject(SQSEvent sqsEvent) {
        String bodyContent = sqsEvent.getRecords().get(0).getBody();
        return new JSONObject(bodyContent);
    }

    public JSONObject extractRequestPayload(JSONObject bodyJsonObject) {
        JSONObject requestPayloadJsonObject = bodyJsonObject.getJSONObject("requestPayload");
        return requestPayloadJsonObject;
    }

    public String amazonRequestId(JSONObject requestPayload) {
        return requestPayload.getJSONArray("Records")
                .getJSONObject(0)
                .getJSONObject("responseElements")
                .getString("x-amz-request-id");
    }

    public List<FailedEvents> getFailedEventsToRetry(int MAX_RETRY, String filterString,
            Map<String, AttributeValue> filterValueMap) {

        // DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
        // .withFilterExpression(filterString)
        // .withLimit(20)
        // .withExpressionAttributeValues(filterValueMap);

        DynamoDBQueryExpression<FailedEvents> queryExpression = new DynamoDBQueryExpression<FailedEvents>()
                .withIndexName("pendingStatusIndex")
                .withKeyConditionExpression("eventStatus = :STATUS and nextRunTimeStamp <= :CURRENT_TIMESTAMP ")
                .withFilterExpression("retryCount < :MAX_RETRY")
                .withExpressionAttributeValues(filterValueMap)
                .withConsistentRead(false)
                .withLimit(20);

        List<FailedEvents> scanResult = mapper.query(FailedEvents.class, queryExpression);
        return scanResult;
    }

    public String nextRunInstant(String currentInstant, int retryCount) {
        return Instant.parse(currentInstant)
                .plus(retryIntervalMap.get(retryCount))
                .toString();
    }

    public void updateFailedEventsTable(FailedEvents failedEvent, String requestId) {
        int updatedRetryCount = failedEvent.getRetryCount()+1;
        UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                    .withPrimaryKey("requestId", requestId)
                    .withUpdateExpression("set eventStatus = :status, retryCount = :retryCountValue, nextRunTimeStamp = :timeStamp")
                    .withValueMap(new ValueMap()
                        .withString(":status", "TRIGGERED")
                        .withInt(":retryCountValue", updatedRetryCount)
                        .withString(":timeStamp", nextRunInstant(failedEvent.getNextRunTimeStamp(), updatedRetryCount)));

        updateItemWithRetry(updateItemSpec);
    }

    public void addElementInFailedEventsTable(String sqsEventString, String requestId) {
        Item item = table.getItem("requestId", requestId);
        if (item == null) {
            logger.log("adding new entry in table");
            table.putItem(new Item()
                    .withPrimaryKey("requestId", requestId)
                    .with("failedEvent", sqsEventString)
                    .with("eventStatus", "PENDING")
                    .with("timeStamp", new Date().getTime())
                    .with("nextRunTimeStamp", nextRunInstant(Instant.now().toString(), 0))
                    .with("retryCount", 0));
        } else {
            logger.log("updating existing entry in table");
            UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                    .withPrimaryKey("requestId", requestId)
                    .withUpdateExpression("set eventStatus = :status")
                    .withValueMap(new ValueMap()
                            .withString(":status", "PENDING"));

            updateItemWithRetry(updateItemSpec);
        }
    }

    public void updateItemWithRetry(UpdateItemSpec updateItemSpec) {
        UpdateItemOutcome updateItemOutcome = null;
        int retryCount = 0;
        while (retryCount <= MAX_RETRY && updateItemOutcome == null) {
            try {
                updateItemOutcome = table.updateItem(updateItemSpec);
            } catch (Exception exception) {
                logger.log(String.format("Unable to update item:  {}", exception.getMessage()));
                if (retryCount == MAX_RETRY)
                    throw exception;
                retryCount++;
            }
        }
    }

    protected void moveFileToArchiveBucket(AmazonS3 s3Client, String sourceBucket, String sourceKey, String destBucket,
            String destKey) {
        CopyObjectResult copyObjectResult = null;
        int retryCount = 0;
        while (retryCount <= MAX_RETRY && copyObjectResult != null) {
            try {
                copyObjectResult = s3Client.copyObject(sourceBucket, sourceKey, destBucket, destKey);
            } catch (SdkClientException exception) {
                String message = String.format("failed to copy file in archive bucket in attempt #{} with exception {}",
                        retryCount, exception.getMessage());
                logger.log(message);
                if (retryCount == MAX_RETRY)
                    throw exception;
                retryCount++;
            }
        }

        for (retryCount = 0; retryCount <= MAX_RETRY; retryCount++) {
            try {
                s3Client.deleteObject(sourceBucket, sourceKey);
                break;
            } catch (SdkClientException exception) {
                String message = String.format("failed to delete file in attempt #%d with exception %s", retryCount,
                        exception.getMessage());
                logger.log(message);
                if (retryCount == MAX_RETRY)
                    throw exception;
            }
        }
    }
}
