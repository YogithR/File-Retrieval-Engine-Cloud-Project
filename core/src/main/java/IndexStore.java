package core;

import java.util.List;
import java.util.Map;

/** Contract for the cloud IndexStore (DynamoDB impl will come next). */
public interface IndexStore {
    long putDocument(String clientId, String relativePath);
    String getDocument(long docId);
    void updateIndex(long docId, Map<String, Integer> termFreqs);
    /** Returns list of [docId, freq] pairs for a term. */
    List<long[]> lookupIndex(String term);
}
