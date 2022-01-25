#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from '@aws-cdk/core';
import { S3ToKafkaWithLambdaStack } from '../lib/s3-to-kafka-with-lambda-stack';
import {KafkaStack} from '../lib/kafka-stack';
import {VpcStack} from "../lib/vpc-stack";
import {KafkaTopicStack} from "../lib/kafka-topic-stack";
import { S3BucketStack } from '../lib/s3-bucket-stack';
import { S3TriggeredLambdaStack } from '../lib/s3-triggered-lambda-stack';
import { LambdaErrorHandlingStack} from '../lib/lambda-error-handling-stack';
import { ArchiveBucketStack } from '../lib/archive-bucket-stack';
import { ApiTriggeredLambdaStack } from '../lib/api-triggered-lambda-stack';
import { HTTPApiStack } from '../lib/http-api-stack';

const app = new cdk.App();

let vpcStack = new VpcStack(app, 'VpcStack');

let kafkaStack = new KafkaStack(vpcStack, app, 'KafkaStack');
kafkaStack.addDependency(vpcStack);

let kafkaTopicStack = new KafkaTopicStack(vpcStack, kafkaStack, app, 'KafkaTopicStack');
kafkaTopicStack.addDependency(vpcStack);
kafkaTopicStack.addDependency(kafkaStack);

let archiveBucketStack = new ArchiveBucketStack(app, 'ArchiveBucketStack');

let s3TriggeredLambdaStack = new S3TriggeredLambdaStack(vpcStack, kafkaStack, archiveBucketStack, app, 'S3TriggeredLambdaStack');
s3TriggeredLambdaStack.addDependency(vpcStack);
s3TriggeredLambdaStack.addDependency(kafkaStack);
//s3TriggeredLambdaStack.addDependency(archiveBucketStack);

let lambdaErrorHandlingStack = new LambdaErrorHandlingStack(s3TriggeredLambdaStack, app, 'LambdaErrorHandlingStack');
lambdaErrorHandlingStack.addDependency(s3TriggeredLambdaStack);

let apiTriggeredLambdaStack = new ApiTriggeredLambdaStack(app, 'ApiTriggeredLambdaStack');

let httpApiStack = new HTTPApiStack(app, 'HTTPApiStack')