import { Stack, StackProps, Construct, RemovalPolicy, Duration, CfnOutput} from '@aws-cdk/core';
import {Runtime, Function, Code} from "@aws-cdk/aws-lambda";
import { LambdaErrorHandlingStack } from './lambda-error-handling-stack';
import * as apigateway from '@aws-cdk/aws-apigateway';

export class ApiTriggeredLambdaStack extends Stack {
    
    constructor(scope: Construct, id: string, props?: StackProps) {
      super(scope, id, props);
      

      const apiGateway = new apigateway.RestApi(this, 'api', {
        description: 'API Gateway',
        deployOptions: {
          stageName: 'test',
        },
        defaultCorsPreflightOptions: {
          allowHeaders: [
            'Content-Type',
            'X-Amz-Date',
            'Authorization',
            'X-Api-Key',
          ],
          allowMethods: ['OPTIONS', 'GET', 'POST', 'PUT', 'PATCH', 'DELETE'],
          allowCredentials: true,
          allowOrigins: ['http://*'],
        },
        
      });

      new CfnOutput(this, 'apiUrl', {value: apiGateway.url});

      const apigatewayRequestsHandlingLambda = new Function(this, 'apigatewayRequestsHandlingLambda', {
        runtime: Runtime.JAVA_11,
        handler: 'com.nodomain.ManuallyTriggeredLambdaHandler',
        code: Code.fromAsset("./asset/handlers-1.0-SNAPSHOT.jar"),
        reservedConcurrentExecutions: 1,
        timeout: Duration.minutes(5),
        memorySize: 512,
        environment: {
            'TABLE_NAME': 'lambdaErrorHandlingStack.tableName',
        }
      });

      const failedEventResource = apiGateway.root.addResource('failedEvents');
      const getFailedEventsResource = failedEventResource.addResource('getfailedEvents');
      getFailedEventsResource.addMethod(
        'GET',
        new apigateway.LambdaIntegration(apigatewayRequestsHandlingLambda, {proxy: true}),
      );

      const postFailedEventsResource = failedEventResource.addResource('{requestId}');
      postFailedEventsResource.addMethod(
        'POST',
        new apigateway.LambdaIntegration(apigatewayRequestsHandlingLambda, {proxy: true}),
      );
    
      //apigatewayRequestsHandlingLambda.grantInvoke(apiGateway);
    }
}