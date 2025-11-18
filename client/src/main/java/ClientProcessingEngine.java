package client;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import java.nio.charset.StandardCharsets;

public class ClientProcessingEngine {
    private final LambdaClient lambda = LambdaClient.create();

    public String register() {
        var req = InvokeRequest.builder()
                .functionName("RegisterHandler")
                .payload(SdkBytes.fromString("{}", StandardCharsets.UTF_8))
                .build();
        var res = lambda.invoke(req);
        return res.payload().asUtf8String();
    }

    public String computeIndex(String jsonPayload) {
        var req = InvokeRequest.builder()
                .functionName("ComputeIndexHandler")
                .payload(SdkBytes.fromString(jsonPayload, StandardCharsets.UTF_8))
                .build();
        var res = lambda.invoke(req);
        return res.payload().asUtf8String();
    }

    public String computeSearch(String jsonPayload) {
        var req = InvokeRequest.builder()
                .functionName("ComputeSearchHandler")
                .payload(SdkBytes.fromString(jsonPayload, StandardCharsets.UTF_8))
                .build();
        var res = lambda.invoke(req);
        return res.payload().asUtf8String();
    }
}
