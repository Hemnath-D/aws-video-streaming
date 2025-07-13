package myproject;

import com.pulumi.Pulumi;
import com.pulumi.asset.FileArchive;
import com.pulumi.aws.apigateway.*;
import com.pulumi.aws.iam.IamFunctions;
import com.pulumi.aws.iam.Role;
import com.pulumi.aws.iam.RoleArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementPrincipalArgs;
import com.pulumi.aws.iam.outputs.GetPolicyDocumentResult;
import com.pulumi.aws.lambda.Function;
import com.pulumi.aws.lambda.FunctionArgs;
import com.pulumi.core.Output;
import com.pulumi.resources.CustomResourceOptions;

import java.util.Map;


public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {

            var assumeRolePolicy = """
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

            var iamForLambda = new Role("iamForLambda", RoleArgs.builder()
                    .name("iam_for_lambda")
                    .assumeRolePolicy(assumeRolePolicy)
                    .build());

            var testLambda = new Function("testLambda", FunctionArgs.builder()
                    .name("lambda_function_name")
                    .role(iamForLambda.arn())
                    .runtime("java21")
                    .handler("org.hemz.ExampleHandler::handleRequest")
                    .code(new FileArchive("C:\\Users\\hemna\\Code\\lambda-test\\target\\lambda-test-1.0-SNAPSHOT.jar"))
                    .build());

            var restApi = new RestApi("video-api", RestApiArgs.builder().build());
            var myDemoResource = new Resource("myDemoResource", ResourceArgs.builder()
                    .restApi(restApi.id())
                    .parentId(restApi.rootResourceId())
                    .pathPart("mydemoresource")
                    .build());

            var myDemoMethod = new Method("myDemoMethod", MethodArgs.builder()
                    .restApi(restApi.id())
                    .resourceId(myDemoResource.id())
                    .httpMethod("GET")
                    .authorization("AWS_IAM")
                    .build());

            var myDemoIntegration = new Integration("myDemoIntegration", IntegrationArgs.builder()
                    .restApi(restApi.id())
                    .resourceId(myDemoResource.id())
                    .httpMethod(myDemoMethod.httpMethod())
                    .integrationHttpMethod("GET")
                    .type("AWS_PROXY")
                    .uri(testLambda.invokeArn().applyValue(uri ->
                            String.format("arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/%s/invocations", uri)))
                    .timeoutMilliseconds(29000)
                    .build(),
                    CustomResourceOptions.builder().dependsOn(testLambda).build());

            var deployment = new Deployment("video-api-deployment", DeploymentArgs.builder()
                    .restApi(restApi.id())
                    .build(),
                    CustomResourceOptions.builder().dependsOn(myDemoIntegration, myDemoMethod).build());

            var stage = new Stage("video-api-stage", StageArgs.builder()
                    .deployment(deployment.id())
                    .stageName("dev")
                    .restApi(restApi.id())
                    .build());
        });
    }
}
