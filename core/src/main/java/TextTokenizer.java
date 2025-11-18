package core;

import java.util.HashMap;
import java.util.Map;

public class TextTokenizer {
    /** Simple tokenizer -> lowercase, strip non [a-z0-9 ], split, count */
    public static Map<String, Integer> termFreqs(String text) {
        Map<String, Integer> tf = new HashMap<>();
        String norm = text == null ? "" : text.toLowerCase().replaceAll("[^a-z0-9 ]", " ");
        for (String t : norm.split("\\s+")) {
            if (t.isBlank()) continue;
            tf.merge(t, 1, Integer::sum);
        }
        return tf;
    }
}
    