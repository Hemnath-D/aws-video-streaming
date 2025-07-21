package myproject;

import com.pulumi.Pulumi;
import com.pulumi.asset.FileArchive;
import com.pulumi.aws.AwsFunctions;
import com.pulumi.aws.apigateway.*;
import com.pulumi.aws.dynamodb.Table;
import com.pulumi.aws.dynamodb.TableArgs;
import com.pulumi.aws.dynamodb.inputs.TableAttributeArgs;
import com.pulumi.aws.dynamodb.inputs.TableOnDemandThroughputArgs;
import com.pulumi.aws.iam.Role;
import com.pulumi.aws.iam.RoleArgs;
import com.pulumi.aws.iam.RolePolicyAttachment;
import com.pulumi.aws.iam.RolePolicyAttachmentArgs;
import com.pulumi.aws.lambda.Function;
import com.pulumi.aws.lambda.FunctionArgs;
import com.pulumi.aws.lambda.Permission;
import com.pulumi.aws.lambda.PermissionArgs;
import com.pulumi.core.Output;
import com.pulumi.resources.CustomResourceOptions;


public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {

            var lambdaRoleTrustPolicy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Action": "sts:AssumeRole",
                      "Principal": {
                        "Service": "lambda.amazonaws.com"
                      },
                      "Effect": "Allow",
                      "Sid": ""
                    }
                  ]
                }
                """;

            var controllerLambdaRole = new Role("controllerLambdaRole", RoleArgs.builder()
                    .name("controller_lambda_role")
                    .assumeRolePolicy(lambdaRoleTrustPolicy)
                    .build());

            // Attach AWS managed policy for DynamoDB full access
            var dynamoPolicyAttachment = new RolePolicyAttachment("dynamoPolicyAttachment", RolePolicyAttachmentArgs.builder()
                    .role(controllerLambdaRole.name())
                    .policyArn("arn:aws:iam::aws:policy/AmazonDynamoDBFullAccess")
                    .build());

            var testLambda = new Function("controllerLambda", FunctionArgs.builder()
                    .name("controller_lambda")
                    .role(controllerLambdaRole.arn())
                    .runtime("java21")
                    .handler("org.hemz.ExampleHandler::handleRequest")
                    .timeout(29)
                    .code(new FileArchive("C:\\Users\\hemna\\Code\\lambda-test\\target\\lambda-test-1.0-SNAPSHOT.jar"))
                    .build());

            var restApi = new RestApi("video-api", RestApiArgs.builder().build());
            var videoResource = new Resource("video", ResourceArgs.builder()
                    .restApi(restApi.id())
                    .parentId(restApi.rootResourceId())
                    .pathPart("video")
                    .build());

            var postVideoMethod = new Method("postVideoMethod", MethodArgs.builder()
                    .restApi(restApi.id())
                    .resourceId(videoResource.id())
                    .httpMethod("POST")
                    .authorization("AWS_IAM")
                    .build());

            Output<String> uri = Output.all(testLambda.arn(), AwsFunctions.getRegion().applyValue(r -> r.name()))
                    .applyValue(values -> {
                        String lambdaArn = values.get(0);
                        String region = values.get(1);
                        return String.format("arn:aws:apigateway:%s:lambda:path/2015-03-31/functions/%s/invocations", region, lambdaArn);
                    });

            var videoIntegration = new Integration("videoIntegration", IntegrationArgs.builder()
                    .restApi(restApi.id())
                    .resourceId(videoResource.id())
                    .httpMethod(postVideoMethod.httpMethod())
                    .integrationHttpMethod("POST")
                    .type("AWS_PROXY")
                    .uri(uri)
                    .timeoutMilliseconds(29000)
                    .build(),
                    CustomResourceOptions.builder().dependsOn(testLambda).build());

            var deployment = new Deployment("video-api-deployment", DeploymentArgs.builder()
                    .restApi(restApi.id())
                    .build(),
                    CustomResourceOptions.builder().dependsOn(videoIntegration, postVideoMethod).build());

            var stage = new Stage("video-api-stage", StageArgs.builder()
                    .deployment(deployment.id())
                    .stageName("dev")
                    .restApi(restApi.id())
                    .build());

            var lambdaPermission = new Permission("videoAPIPermission", PermissionArgs.builder()
                    .statementId("AllowVideoAPIInvoke")
                    .action("lambda:InvokeFunction")
                    .function("controller_lambda")
                    .principal("apigateway.amazonaws.com")
                    .sourceArn(restApi.executionArn().applyValue(_executionArn -> String.format("%s/*", _executionArn)))
                    .build());

            var videoTable = new Table("video", TableArgs.builder()
                    .name("video")
                    .hashKey("id")
                    .rangeKey("epoch")
                    .writeCapacity(5)
                    .readCapacity(5)
                    .attributes(
                            TableAttributeArgs.builder()
                                    .name("id")
                                    .type("S")
                                    .build(),
                            TableAttributeArgs.builder()
                                    .name("epoch")
                                    .type("N")
                                    .build())
                    .build());
        });
    }
}
