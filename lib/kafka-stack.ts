import * as cdk from '@aws-cdk/core';
import * as msk from '@aws-cdk/aws-msk';
import * as ec2 from '@aws-cdk/aws-ec2'

import {VpcStack} from "./vpc-stack";

export class KafkaStack extends cdk.Stack {
    public kafkaCluster: msk.CfnCluster;

    constructor(vpcStack: VpcStack, scope: cdk.Construct, id: string, props?: cdk.StackProps) {
        super(scope, id, props);
        this.kafkaCluster = new msk.CfnCluster(this, "kafkaCluster", {
            brokerNodeGroupInfo: {
                securityGroups: [vpcStack.kafkaSecurityGroup.securityGroupId],
                clientSubnets: [...vpcStack.vpc.selectSubnets({
                    subnetType: ec2.SubnetType.PRIVATE
                }).subnetIds],
                instanceType: "kafka.t3.small",
                storageInfo: {
                    ebsStorageInfo: {
                        volumeSize: 5
                    }
                }
            },
            clusterName: "TransactionsKafkaCluster",
            kafkaVersion: "2.7.0",
            numberOfBrokerNodes: 2
        });
    }
}
