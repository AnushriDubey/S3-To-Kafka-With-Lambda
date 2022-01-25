package com.nodomain;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import com.amazonaws.services.s3.model.S3Object;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;
import java.util.Scanner;
import java.util.UUID;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import java.util.concurrent.ExecutionException;

public class S3ToKafkaHandler implements RequestHandler<S3Event, String>
{

    private LambdaLogger logger;
    private final String TOPIC = System.getenv("TOPIC_NAME");
    private final String BROKERS = System.getenv("BOOTSTRAP_ADDRESS");
    private final String SUCCESS_EVENT_TABLE = System.getenv("SUCCESS_EVENT_TABLE");
    private final String ARCHIVE_BUCKET_NAME = System.getenv("ARCHIEVE_BUCKET_NAME");
    private final String SECURITY_PROTOCOL_CONFIG = "SSL";
    private KafkaProducer<String, String> producer;
    private Utilities utilities;


    public String handleRequest(S3Event s3event, Context context) {
        logger = context.getLogger();
        utilities = new Utilities();
        utilities.logger = logger;

        S3EventNotification.S3EventNotificationRecord record = s3event.getRecords().get(0);
        String sourceBucket = record.getS3().getBucket().getName();
        logger.log("founded source bucket: "+sourceBucket);
        
        // Object key may have spaces or unicode non-ASCII characters.
        String sourceKey = record.getS3().getObject().getUrlDecodedKey();
        logger.log("founded source key: "+ sourceKey);

        AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
        S3Object s3Object = s3Client.getObject(new GetObjectRequest(sourceBucket, sourceKey));

        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
	    DynamoDB dynamoDb = new DynamoDB(client);
        String fileUniqueIdentifier = sourceBucket + "/" + sourceKey;

        if(!isEventProcessedBefore(dynamoDb, fileUniqueIdentifier)) {
            logger.log("Processing this event " + fileUniqueIdentifier);
            InputStream objectData = s3Object.getObjectContent();
            logger.log(s3event.toString());
            createProducer();
            logger.log("creted producer to send messages");
            
            readDataAndSendMsg(objectData);
            logger.log("messages sent successfully, marking event as success");
            
            addSuccessEventsInDynamodbTable(fileUniqueIdentifier, dynamoDb);
            
            logger.log("moving file to archieve bucket");
            utilities.moveFileToArchiveBucket(s3Client, sourceBucket, sourceKey, ARCHIVE_BUCKET_NAME, "archive"+sourceKey);
        }
        return "OK";
    }

    protected void createProducer() {
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BROKERS);
        properties.setProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SECURITY_PROTOCOL_CONFIG);
        properties.setProperty(ProducerConfig.CLIENT_ID_CONFIG, "DeomoProducer");
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        producer = new KafkaProducer<String,String>(properties);
    }

    protected void readDataAndSendMsg(InputStream inputStream) {
        try(Scanner sc = new Scanner(inputStream)) {
            while(sc.hasNextLine()) {
                try{
                    String value = sc.nextLine();
                    String key = UUID.randomUUID().toString();
                    producer.send(new ProducerRecord<String, String>(TOPIC, key, value)).get();
                    //int divide = 5/0;
                }
                catch(InterruptedException|ExecutionException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected boolean isEventProcessedBefore(DynamoDB dynamoDB, String keyBucketPair) {
        Table table = dynamoDB.getTable(SUCCESS_EVENT_TABLE);
        Item item = table.getItem("keyBucketPair", keyBucketPair);
        if(item !=null) logger.log("Event already procossed successfully at " + item.getString("timeStamp"));
        return item != null;
    }

    protected void addSuccessEventsInDynamodbTable(String keyBucketPair, DynamoDB dynamoDb) {
        Table table = dynamoDb.getTable(SUCCESS_EVENT_TABLE);
        table.putItem(new Item()
                    .withPrimaryKey("keyBucketPair", keyBucketPair)
                    .with("eventStatus", "SUCCESS")
                    .with("timeStamp", new Date().toString()));

    }

}

