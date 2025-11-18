// package client;

// import java.util.Scanner;

// /** Minimal CLI to prove the build + invoke path works. */
// public class App {
//     public static void main(String[] args) {
//         ClientProcessingEngine eng = new ClientProcessingEngine();
//         Scanner sc = new Scanner(System.in);
//         System.out.println("Cloud FRE Client. Commands: register | index-json <j> | search-json <j> | quit");

//         while (true) {
//             System.out.print("> ");
//             String cmd = sc.nextLine().trim();
//             if (cmd.equalsIgnoreCase("quit")) break;

//             try {
//                 if (cmd.equalsIgnoreCase("register")) {
//                     System.out.println(eng.register());
//                 } else if (cmd.startsWith("index-json ")) {
//                     String json = cmd.substring("index-json ".length());
//                     System.out.println(eng.computeIndex(json));
//                 } else if (cmd.startsWith("search-json ")) {
//                     String json = cmd.substring("search-json ".length());
//                     System.out.println(eng.computeSearch(json));
//                 } else {
//                     System.out.println("Unknown. Try: register | index-json {..} | search-json {..} | quit");
//                 }
//             } catch (Exception e) {
//                 e.printStackTrace();
//             }
//         }
//         System.out.println("Bye.");
//     }
// }







package client;

import core.TextTokenizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class App {
    private static final ObjectMapper M = new ObjectMapper();
    private static final LambdaClient LAMBDA = LambdaClient.builder()
            .region(Region.of(Optional.ofNullable(System.getenv("AWS_REGION")).orElse("us-east-1")))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();

    private static String clientId = null;

    public static void main(String[] args) throws Exception {
        System.out.println("Cloud FRE Client. Commands: register | index-json <j> | index-file <path> | search-json <j> | pwd | quit");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        while (true) {
            System.out.print("> ");
            String line = br.readLine();
            if (line == null) break;
            line = line.trim();
            if (line.isEmpty()) continue;

            try {
                if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                    System.out.println("Bye.");
                    break;
                } else if (line.equalsIgnoreCase("pwd")) {
                    System.out.println("CWD: " + Path.of(System.getProperty("user.dir")).toAbsolutePath());
                } else if (line.equalsIgnoreCase("register")) {
                    Map<String, Object> resp = invoke("RegisterHandler", Map.of());
                    clientId = (String) resp.get("clientId");
                    System.out.println(toJson(resp));
                } else if (line.startsWith("index-json")) {
                    String json = line.substring("index-json".length()).trim();
                    Map<String, Object> payload = M.readValue(json, Map.class);
                    if (!payload.containsKey("clientId") && clientId != null) {
                        payload = new HashMap<>(payload);
                        payload.put("clientId", clientId);
                    }
                    Map<String, Object> resp = invoke("ComputeIndexHandler", payload);
                    System.out.println(toJson(resp));
                } else if (line.startsWith("index-file")) {
                    String pathArg = line.substring("index-file".length()).trim();
                    if (pathArg.isBlank()) { System.out.println("Usage: index-file <path>"); continue; }

                    Path p = resolvePath(pathArg);
                    if (!Files.exists(p)) {
                        System.out.println("File not found: " + p.toAbsolutePath());
                        continue;
                    }
                    if (clientId == null) {
                        Map<String,Object> r = invoke("RegisterHandler", Map.of());
                        clientId = (String) r.get("clientId");
                    }
                    String text = Files.readString(p, StandardCharsets.UTF_8);
                    Map<String, Integer> tf = TextTokenizer.termFreqs(text);
                    Map<String, Object> payload = Map.of(
                            "clientId", clientId,
                            "docPath", p.toString(),
                            "termFreqs", tf
                    );
                    Map<String, Object> resp = invoke("ComputeIndexHandler", payload);
                    System.out.println(toJson(resp));
                } else if (line.startsWith("search-json")) {
                    String json = line.substring("search-json".length()).trim();
                    Map<String, Object> payload = M.readValue(json, Map.class);
                    Map<String, Object> resp = invoke("ComputeSearchHandler", payload);
                    System.out.println(toJson(resp));
                } else {
                    System.out.println("Unknown command.");
                }
            } catch (Exception ex) {
                // Prevent the exec-maven-plugin session from dying on a single error
                System.out.println("ERROR: " + ex.getClass().getSimpleName() + " - " + safeMsg(ex.getMessage()));
            }
        }
    }

    // Resolve relative paths robustly: try CWD (client/) then project root
    private static Path resolvePath(String path) {
        Path p = Path.of(path);
        if (p.isAbsolute()) return p;
        if (Files.exists(p)) return p; // usually when you placed file in client/
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path rootTry = (cwd.getParent() != null) ? cwd.getParent().resolve(path) : p;
        if (Files.exists(rootTry)) return rootTry; // file at project root
        return p; // will be reported as not found
    }

    private static Map<String, Object> invoke(String functionName, Map<String, Object> payload) throws Exception {
        byte[] bytes = M.writeValueAsBytes(payload);
        InvokeRequest req = InvokeRequest.builder()
                .functionName(functionName)
                .payload(SdkBytes.fromByteArray(bytes))
                .build();
        var resp = LAMBDA.invoke(req);
        byte[] out = resp.payload().asByteArray();
        return M.readValue(out, Map.class);
    }

    private static String toJson(Object o) throws Exception {
        return M.writeValueAsString(o);
    }

    private static String safeMsg(String s) {
        if (s == null) return "";
        return s.replace('\n',' ').replace('\r',' ');
    }
}

