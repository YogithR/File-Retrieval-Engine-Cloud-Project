// package lambda;

// import com.amazonaws.services.lambda.runtime.Context;
// import com.amazonaws.services.lambda.runtime.RequestHandler;
// import java.util.Map;

// /**
//  * Input JSON (example):
//  * { "clientId":"...", "docPath":"...", "termFreqs":{"hello":3,"world":2} }
//  */
// public class ComputeIndexHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
//     @Override
//     public Map<String, Object> handleRequest(Map<String, Object> input, Context ctx) {
//         String docPath = (String) input.getOrDefault("docPath", "");
//         // TODO: call DynamoDB-backed IndexStore here in the next step
//         return Map.of("status", "OK", "indexed", docPath);
//     }
// }





// package lambda;

// import com.amazonaws.services.lambda.runtime.Context;
// import com.amazonaws.services.lambda.runtime.RequestHandler;
// import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
// import software.amazon.awssdk.regions.Region;
// import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
// import software.amazon.awssdk.services.dynamodb.model.*;

// import java.util.*;

// public class ComputeIndexHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

//     private final DynamoDbClient ddb =
//             DynamoDbClient.builder()
//                     .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1")))
//                     .credentialsProvider(DefaultCredentialsProvider.create())
//                     .build();

//     private final String DOCMAP    = System.getenv("TABLE_DOCMAP");    // FRE_DocumentMap
//     private final String TERMIDX   = System.getenv("TABLE_TERMIDX");   // FRE_TermIndex
//     private final String COUNTERS  = System.getenv("TABLE_COUNTERS");  // FRE_Counters

//     @Override
//     public Map<String, Object> handleRequest(Map<String, Object> input, Context ctx) {
//         // input: { "clientId":"...", "docPath":"folder/book1.txt", "termFreqs":{"alice":3,"wonderland":5} }
//         String docPath = (String) input.get("docPath");

//         @SuppressWarnings("unchecked")
//         Map<String, Object> termFreqs = (Map<String, Object>) input.getOrDefault("termFreqs", Map.of());

//         // 1) get next docId
//         int docId = nextDocId();

//         // 2) put into document map
//         PutItemRequest putDoc = PutItemRequest.builder()
//                 .tableName(DOCMAP)
//                 .item(Map.of(
//                         "docId", AttributeValue.builder().n(Integer.toString(docId)).build(),
//                         "path",  AttributeValue.builder().s(docPath).build()
//                 ))
//                 .build();
//         ddb.putItem(putDoc);

//         // 3) write postings to term index
//         for (Map.Entry<String, Object> e : termFreqs.entrySet()) {
//             String term = e.getKey();
//             int freq = ((Number) e.getValue()).intValue();

//             PutItemRequest putIdx = PutItemRequest.builder()
//                     .tableName(TERMIDX)
//                     .item(Map.of(
//                             "term",  AttributeValue.builder().s(term).build(),                 // PK
//                             "docId", AttributeValue.builder().n(Integer.toString(docId)).build(), // SK
//                             "freq",  AttributeValue.builder().n(Integer.toString(freq)).build()
//                     ))
//                     .build();
//             ddb.putItem(putIdx);
//         }

//         return Map.of("status", "OK", "indexed", docPath, "docId", docId);
//     }

//     private int nextDocId() {
//         // COUNTERS table has item: { "name":"docSeq", "value":N }
//         Map<String, AttributeValue> key = Map.of("name", AttributeValue.builder().s("docSeq").build());
//         Map<String, AttributeValueUpdate> upd = Map.of(
//                 "value", AttributeValueUpdate.builder()
//                         .value(AttributeValue.builder().n("1").build())
//                         .action(AttributeAction.ADD).build()
//         );
//         UpdateItemResponse resp = ddb.updateItem(UpdateItemRequest.builder()
//                 .tableName(COUNTERS)
//                 .key(key)
//                 .attributeUpdates(upd)
//                 .returnValues(ReturnValue.ALL_NEW)
//                 .build());
//         return Integer.parseInt(resp.attributes().get("value").n());
//     }
// }






package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;

public class ComputeIndexHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient ddb =
            DynamoDbClient.builder()
                    .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1")))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();

    private final String DOCMAP   = System.getenv("TABLE_DOCMAP");    // FRE_DocumentMap
    private final String TERMIDX  = System.getenv("TABLE_TERMIDX");   // FRE_TermIndex
    private final String COUNTERS = System.getenv("TABLE_COUNTERS");  // FRE_Counters

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context ctx) {
        String docPath = (String) input.get("docPath");
        Map<String, Object> termFreqs = (Map<String, Object>) input.getOrDefault("termFreqs", Map.of());

        // 1) next docId
        int docId = nextDocId();

        // 2) put doc map
        ddb.putItem(PutItemRequest.builder()
                .tableName(DOCMAP)
                .item(Map.of(
                        "docId", AttributeValue.builder().n(Integer.toString(docId)).build(),
                        "path",  AttributeValue.builder().s(docPath).build()
                ))
                .build());

        // 3) write postings
        for (Map.Entry<String, Object> e : termFreqs.entrySet()) {
            String term = e.getKey();
            int freq = ((Number) e.getValue()).intValue();

            ddb.putItem(PutItemRequest.builder()
                    .tableName(TERMIDX)
                    .item(Map.of(
                            "term",  AttributeValue.builder().s(term).build(),
                            "docId", AttributeValue.builder().n(Integer.toString(docId)).build(),
                            "freq",  AttributeValue.builder().n(Integer.toString(freq)).build()
                    ))
                    .build());
        }
        return Map.of("status", "OK", "indexed", docPath, "docId", docId);
    }

    private int nextDocId() {
        Map<String, AttributeValue> key = Map.of("name", AttributeValue.builder().s("docSeq").build());
        Map<String, AttributeValueUpdate> upd = Map.of(
                "value", AttributeValueUpdate.builder()
                        .value(AttributeValue.builder().n("1").build())
                        .action(AttributeAction.ADD).build()
        );
        UpdateItemResponse resp = ddb.updateItem(UpdateItemRequest.builder()
                .tableName(COUNTERS)
                .key(key)
                .attributeUpdates(upd)
                .returnValues(ReturnValue.ALL_NEW)
                .build());
        return Integer.parseInt(resp.attributes().get("value").n());
    }
}
