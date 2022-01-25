import { Stack, StackProps, Construct, RemovalPolicy, Duration} from '@aws-cdk/core';
import { S3TriggeredLambdaStack } from './s3-triggered-lambda-stack';
import {AttributeType, Table} from "@aws-cdk/aws-dynamodb";
import {Runtime, Function, Code} from "@aws-cdk/aws-lambda";
import { SqsEventSource} from '@aws-cdk/aws-lambda-event-sources';
import * as events from "@aws-cdk/aws-events";
import * as targets from "@aws-cdk/aws-events-targets";

export class LambdaErrorHandlingStack extends Stack {
    public tableName = "FailedEventsSummary";
    public failedEventsTable: Table;
  constructor(s3TriggeredLambda: S3TriggeredLambdaStack, scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);

    const sqsQueue = s3TriggeredLambda.failedEventsQueue;

    this.failedEventsTable = new Table(this, "dynamoDbTable", {
        tableName: this.tableName,
        partitionKey: {name: "requestId", type: AttributeType.STRING},
        //sortKey: {name: "retryCount", type: AttributeType.NUMBER},
        removalPolicy: RemovalPolicy.DESTROY
    });

    this.failedEventsTable.addGlobalSecondaryIndex({
      indexName: 'pendingStatusIndex',
      partitionKey: {name: 'eventStatus', type: AttributeType.STRING},
      sortKey: {name: 'nextRunTimeStamp', type: AttributeType.STRING}
    })

    const failedEventsLambda = new Function(this, 'failedEventsLambda', {
        runtime: Runtime.JAVA_11,
        handler: 'com.nodomain.FailedEventsHandler',
        code: Code.fromAsset("./asset/handlers-1.0-SNAPSHOT.jar"),
        reservedConcurrentExecutions: 1,
        timeout: Duration.minutes(5),
        memorySize: 512,
        environment: {
            'TABLE_NAME': this.tableName,
        }
      });

    this.failedEventsTable.grantReadWriteData(failedEventsLambda);
    failedEventsLambda.addEventSource(new SqsEventSource(sqsQueue));
    
    //sqsDlq.grantConsumeMessages(failedEventsLambda);
    sqsQueue.grantConsumeMessages(failedEventsLambda);


    const cronScheduledLambda = new Function(this, 'cronScheduledLambda', {
        runtime: Runtime.JAVA_11,
        handler: 'com.nodomain.CronScheduledLambdaHandler',
        code: Code.fromAsset("./asset/handlers-1.0-SNAPSHOT.jar"),
        reservedConcurrentExecutions: 1,
        timeout: Duration.minutes(5),
        memorySize: 512,
        environment: {
            'TABLE_NAME': this.tableName,
        }
      });


      const rule = new events.Rule(this, 'Rule', {
        schedule: events.Schedule.expression('cron(0/3 * ? * * *)')
      });

      s3TriggeredLambda.lambdaFunction.grantInvoke(cronScheduledLambda)
      rule.addTarget(new targets.LambdaFunction(cronScheduledLambda));
      this.failedEventsTable.grantReadWriteData(cronScheduledLambda);
  
  }
}