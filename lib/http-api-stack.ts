import { Stack, StackProps, Construct, RemovalPolicy, Duration } from '@aws-cdk/core';
import * as apigwV2 from '@aws-cdk/aws-apigatewayv2'
import {Runtime, Function, Code} from "@aws-cdk/aws-lambda";
import { HttpLambdaIntegration } from '@aws-cdk/aws-apigatewayv2-integrations';
import { PayloadFormatVersion } from '@aws-cdk/aws-apigatewayv2';

export class HTTPApiStack extends Stack {
    

    constructor(scope: Construct, id: string, props?: StackProps) {
      super(scope, id, props);


      const httpApiRequestLambda = new Function(this, 'httpApiRequestLambda', {
        runtime: Runtime.JAVA_11,
        handler: 'com.nodomain.HttpApiIntegartionLambda',
        code: Code.fromAsset("./asset/handlers-1.0-SNAPSHOT.jar"),
        reservedConcurrentExecutions: 1,
        timeout: Duration.minutes(5),
        memorySize: 512,
        environment: {
            'TABLE_NAME': 'lambdaErrorHandlingStack.tableName',
        }
      });

      const httpApiIntegration = new HttpLambdaIntegration('httpApiIntegration', 
        httpApiRequestLambda);

      const httpApi = new apigwV2.HttpApi(this, 'HttpApi', {
        description: 'Http Api',
        corsPreflight: {
            allowHeaders: ['Authorization'],
            allowMethods: [
              apigwV2.CorsHttpMethod.GET,
              apigwV2.CorsHttpMethod.HEAD,
              apigwV2.CorsHttpMethod.OPTIONS,
              apigwV2.CorsHttpMethod.POST,
            ],
            allowOrigins: ['http://*'],
            allowCredentials: true,
            exposeHeaders: ['Date', 'x-api-id'],
            maxAge: Duration.days(10),
          },
          createDefaultStage: false,
          defaultIntegration: httpApiIntegration,
          disableExecuteApiEndpoint: false
      });

      new apigwV2.HttpStage(this, 'Stage', {
        httpApi: httpApi,
        stageName: 'test',
        autoDeploy: true
      });

      
      
      httpApi.addRoutes({
          path: '/failedEvents',
          methods: [apigwV2.HttpMethod.GET],
          integration: httpApiIntegration
      });

      httpApi.addRoutes({
        path: '/triggerEvents',
        methods: [apigwV2.HttpMethod.POST],
        integration: httpApiIntegration
    });
    //httpApiRequestLambda.grantInvoke(httpApi);
}
}