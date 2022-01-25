import { Stack, StackProps, Construct, RemovalPolicy} from '@aws-cdk/core';
import * as s3 from '@aws-cdk/aws-s3';

export class S3BucketStack extends Stack {
    public s3Bucket: s3.Bucket
  constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);

    this.s3Bucket = new s3.Bucket(this, 's3-kafka-testing', {
        //versioned: true,
        removalPolicy: RemovalPolicy.DESTROY,
        autoDeleteObjects: true
    });
  }
}