import { Stack, StackProps, Construct, RemovalPolicy, Duration } from '@aws-cdk/core';
import * as s3 from '@aws-cdk/aws-s3'


export class ArchiveBucketStack extends Stack {
    public archiveBucket: s3.Bucket;

    constructor(scope: Construct, id: string, props?: StackProps) {
      super(scope, id, props);
      this.archiveBucket = new s3.Bucket(this, 's3-archive-bucket', {
        removalPolicy: RemovalPolicy.DESTROY,
        autoDeleteObjects: true
    });
}
}