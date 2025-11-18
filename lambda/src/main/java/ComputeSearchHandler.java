// package lambda;

// import com.amazonaws.services.lambda.runtime.Context;
// import com.amazonaws.services.lambda.runtime.RequestHandler;
// import java.util.*;

// /**
//  * Input JSON (example):
//  * { "terms": ["cats","dogs"] }
//  */
// public class ComputeSearchHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
//     @Override
//     public Map<String, Object> handleRequest(Map<String, Object> input, Context ctx) {
//         // TODO: query DynamoDB in next step; stub a fake result now
//         List<Map<String, Object>> results = List.of(
//             Map.of("path", "/tmp/example.txt", "score", 42)
//         );
//         return Map.of("results", results, "count", results.size());
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
// import java.util.stream.Collectors;

// public class ComputeSearchHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

//     private final DynamoDbClient ddb =
//             DynamoDbClient.builder()
//                     .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1")))
//                     .credentialsProvider(DefaultCredentialsProvider.create())
//                     .build();

//     private final String DOCMAP = System.getenv("TABLE_DOCMAP");   // FRE_DocumentMap
//     private final String TERMIDX = System.getenv("TABLE_TERMIDX"); // FRE_TermIndex

//     @Override
//     public Map<String, Object> handleRequest(Map<String, Object> input, Context ctx) {
//         @SuppressWarnings("unchecked")
//         List<String> terms = (List<String>) input.getOrDefault("terms", List.of());

//         Map<Integer, Double> scores = new HashMap<>();

//         for (String term : terms) {
//             QueryRequest q = QueryRequest.builder()
//                     .tableName(TERMIDX)
//                     .keyConditionExpression("term = :t")
//                     .expressionAttributeValues(Map.of(":t", AttributeValue.builder().s(term).build()))
//                     .build();

//             QueryResponse qr = ddb.query(q);
//             for (Map<String, AttributeValue> item : qr.items()) {
//                 int docId = Integer.parseInt(item.get("docId").n());
//                 double freq = item.containsKey("freq")
//                         ? Double.parseDouble(item.get("freq").n())
//                         : 1.0; // default if you didn't store freq
//                 scores.merge(docId, freq, Double::sum);
//             }
//         }

//         List<Map<String, Object>> results = scores.entrySet().stream()
//                 .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
//                 .map(e -> {
//                     int docId = e.getKey();
//                     String path = fetchPath(docId);

//                     Map<String, Object> row = new LinkedHashMap<>();
//                     row.put("path", path);
//                     row.put("score", e.getValue());
//                     return row;
//                 })
//                 .collect(Collectors.toList());

//         return Map.of("results", results, "count", results.size());
//     }

//     private String fetchPath(int docId) {
//     Map<String, AttributeValue> key = Map.of(
//         "docId", AttributeValue.builder().n(Integer.toString(docId)).build()
//     );

//     GetItemRequest get = GetItemRequest.builder()
//             .tableName(DOCMAP)
//             .key(key)
//             .projectionExpression("#p")                 // alias instead of "path"
//             .expressionAttributeNames(Map.of("#p","path"))
//             .build();

//     Map<String, AttributeValue> item = ddb.getItem(get).item();
//     return (item != null && item.containsKey("path"))
//             ? item.get("path").s()  
//             : ("docId:" + docId);
//     }
// }




package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class ComputeSearchHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient ddb =
            DynamoDbClient.builder()
                    .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1")))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();

    private final String DOCMAP = System.getenv("TABLE_DOCMAP");   // FRE_DocumentMap
    private final String TERMIDX = System.getenv("TABLE_TERMIDX"); // FRE_TermIndex

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context ctx) {
        List<String> terms = (List<String>) input.getOrDefault("terms", List.of());

        // 1) accumulate scores per docId
        Map<Integer, Double> scores = new HashMap<>();
        for (String term : terms) {
            QueryRequest q = QueryRequest.builder()
                    .tableName(TERMIDX)
                    .keyConditionExpression("term = :t")
                    .expressionAttributeValues(Map.of(":t", AttributeValue.builder().s(term).build()))
                    .build();

            QueryResponse qr = ddb.query(q);
            for (Map<String, AttributeValue> item : qr.items()) {
                int docId = Integer.parseInt(item.get("docId").n());
                double freq = item.containsKey("freq")
                        ? Double.parseDouble(item.get("freq").n())
                        : 1.0;
                scores.merge(docId, freq, Double::sum);
            }
        }

        // 2) rank by score
        List<Map.Entry<Integer, Double>> ranked = new ArrayList<>(scores.entrySet());
        ranked.sort((a,b) -> Double.compare(b.getValue(), a.getValue()));

        if (ranked.isEmpty()) {
            return Map.of("results", List.of(), "count", 0);
        }

        // 3) batch-get paths from DocumentMap (alias 'path' because it's reserved)
        List<Map<String, AttributeValue>> keys = ranked.stream()
                .map(e -> Map.of("docId", AttributeValue.builder().n(Integer.toString(e.getKey())).build()))
                .collect(Collectors.toList());

        BatchGetItemRequest batchReq = BatchGetItemRequest.builder()
                .requestItems(Map.of(
                        DOCMAP, KeysAndAttributes.builder()
                                .keys(keys)
                                .projectionExpression("#p, docId")
                                .expressionAttributeNames(Map.of("#p", "path"))
                                .build()
                ))
                .build();

        BatchGetItemResponse batchResp = ddb.batchGetItem(batchReq);
        List<Map<String, AttributeValue>> docs = batchResp.responses().getOrDefault(DOCMAP, List.of());

        Map<Integer, String> idToPath = new HashMap<>();
        for (Map<String, AttributeValue> item : docs) {
            int id = Integer.parseInt(item.get("docId").n());
            String path = item.containsKey("path") ? item.get("path").s() : ("docId:" + id);
            idToPath.put(id, path);
        }

        // 4) build results (unique, sorted)
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map.Entry<Integer, Double> e : ranked) {
            int docId = e.getKey();
            String path = idToPath.getOrDefault(docId, "docId:" + docId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", path);
            row.put("score", e.getValue());
            results.add(row);
        }

        return Map.of("results", results, "count", results.size());
    }
}
