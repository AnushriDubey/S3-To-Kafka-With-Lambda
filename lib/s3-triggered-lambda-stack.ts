import { Stack, StackProps, Construct, CfnParameter, Duration, RemovalPolicy} from '@aws-cdk/core';
import {S3BucketStack} from './s3-bucket-stack'
import {Queue} from '@aws-cdk/aws-sqs';
import {SqsDestination} from '@aws-cdk/aws-lambda-destinations';
import { VpcStack } from './vpc-stack';
import { KafkaStack } from './kafka-stack';
import {Effect, PolicyStatement} from "@aws-cdk/aws-iam";
import {AttributeType, Table} from "@aws-cdk/aws-dynamodb";
import {Runtime, Function, Code} from "@aws-cdk/aws-lambda";
import * as s3 from '@aws-cdk/aws-s3';
import { S3EventSource} from '@aws-cdk/aws-lambda-event-sources';
import { ArchiveBucketStack } from './archive-bucket-stack';

export class S3TriggeredLambdaStack extends Stack {

    private successEventsTableName = "SuccessEventSummary";
    public failedEventsQueue: Queue;
    public lambdaFunction: Function;
  
  constructor(vpcStack: VpcStack, kafkaStack: KafkaStack, archiveBucketStack: ArchiveBucketStack, scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);


    let bootstrapAddress = new CfnParameter(this, "bootstrapAddress", {
        type: "String",
        description: "Bootstrap address for Kafka broker. Corresponds to bootstrap.servers Kafka consumer configuration"
    });

    let topicName = new CfnParameter(this, "topicName", {
        type: "String",
        description: "Kafka topic name"
    });

    const s3Bucket = new s3.Bucket(this, 's3-kafka-testing', {
        //versioned: true,
        removalPolicy: RemovalPolicy.DESTROY,
        autoDeleteObjects: true
    });


    // dead letter queue for SQS queue used when consumer failed to consume event.
    const deadLetterQueue = new Queue(this, 'deadLetterQueue'); 

    this.failedEventsQueue = new Queue(this, 'queueDestination',
        {
            deadLetterQueue: {
                queue: deadLetterQueue,
                maxReceiveCount: 2
            },
            visibilityTimeout: Duration.seconds(300)
        });
    
    const sqsDestination = new SqsDestination(this.failedEventsQueue);

    const successTable = new Table(this, "successTable", {
        tableName: this.successEventsTableName,
        partitionKey: {name: "keyBucketPair", type: AttributeType.STRING},
        removalPolicy: RemovalPolicy.DESTROY,
        
    });

    this.lambdaFunction = new Function(this, 'lambdaFunction', {
        runtime: Runtime.JAVA_11,
        handler: 'com.nodomain.S3ToKafkaHandler',
        code: Code.fromAsset("./asset/handlers-1.0-SNAPSHOT.jar"),
        reservedConcurrentExecutions: 1,
        timeout: Duration.minutes(5),
        vpc: vpcStack.vpc,
        retryAttempts: 0,
        //maxEventAge: Duration.minutes(4),
        onFailure: sqsDestination,
        //deadLetterQueue: sqsDlq,
        securityGroups: [vpcStack.lambdaSecurityGroup],
        memorySize: 512,
        environment: {
            'BOOTSTRAP_ADDRESS': bootstrapAddress.valueAsString,
            'TOPIC_NAME': topicName.valueAsString,
            'SUCCESS_EVENT_TABLE': this.successEventsTableName,
            'ARCHIVE_BUCKET_NAME': archiveBucketStack.archiveBucket.bucketName
        }
      });

    successTable.grantReadWriteData(this.lambdaFunction);
    s3Bucket.grantRead(this.lambdaFunction);
    archiveBucketStack.archiveBucket.grantWrite(this.lambdaFunction);
    
    //s3BucketStack.s3Bucket.addEventNotification(s3.EventType.OBJECT_CREATED, new LambdaDestination(this.lambdaFunction));
    
    this.lambdaFunction.addEventSource(new S3EventSource(s3Bucket, {
        events: [s3.EventType.OBJECT_CREATED]        
    }));


    this.lambdaFunction.addToRolePolicy(new PolicyStatement({
        effect: Effect.ALLOW,
        actions: ['kafka:*'],
        resources: [kafkaStack.kafkaCluster.ref]
    }));
    


  }
}