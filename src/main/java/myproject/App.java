package myproject;

import com.pulumi.Pulumi;
import com.pulumi.aws.apigateway.*;
import com.pulumi.resources.CustomResourceOptions;


public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {
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
                    .authorization("NONE")
                    .build());

            var deployment = new Deployment("video-api-deployment", DeploymentArgs.builder()
                    .restApi(restApi.id())
                    .build(),
                    CustomResourceOptions.builder().dependsOn(restApi, myDemoMethod, myDemoResource).build());

            var myDemoIntegration = new Integration("myDemoIntegration", IntegrationArgs.builder()
                    .restApi(restApi.id())
                    .resourceId(myDemoResource.id())
                    .httpMethod(myDemoMethod.httpMethod())
                    .type("MOCK")
                    .timeoutMilliseconds(29000)
                    .build());
        });
    }
}
