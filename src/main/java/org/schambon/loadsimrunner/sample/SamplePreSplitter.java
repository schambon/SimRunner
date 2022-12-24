package org.schambon.loadsimrunner.sample;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.schambon.loadsimrunner.PreSplitter;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

public class SamplePreSplitter implements PreSplitter {
  @Override
  public void preSplit(MongoClient client, String namespace) {

    MongoDatabase admindb = client.getDatabase("admin");
    MongoDatabase configdb = client.getDatabase("config");
    String database = namespace.split("[.]")[0];

    // Get primary shard
    Document databaseConfig = configdb.getCollection("databases").find(eq("_id", database)).first();

    // Get list of shards
    Document shardList = admindb.runCommand(new Document("listShards", 1));
    List<Document> shards = shardList.getList("shards", Document.class);

    // Build shard list
    List<String> shardNames = new ArrayList<>();
    int index = 0;

    // Start by adding the primary shard
    shardNames.add(index, (String) databaseConfig.get("primary"));
    index++;

    // Add all remaining shards
    for (Document shard : shards) {
      String shardName = (String) shard.get("_id");
      if (!shardNames.contains(shardName)) {
        shardNames.add(index, shardName);
        index++;
      }
    }

    // Create chunks
    int numChunks = 0;
    for (int x = 33; x < 127; x++) {
      // creating chunks using ASCII chars
      var prefix = Character.toString((char) x);
      admindb.runCommand(new Document("split", namespace)
          .append("middle", new Document("first", prefix)));
      numChunks++;
    }

    int numChunksPerShard = numChunks / shardNames.size();
    int rem = numChunks % shardNames.size();
    int counter = numChunksPerShard;
    // Move chunks
    int y = 1;
    for (int x = 33 + counter; x < 127; x++) {

      // Start with 2nd shard, then move to the next shard
      if (counter == numChunksPerShard * (y + 1) + rem) {
        y++;
      }

      var prefix = Character.toString((char) x);
      admindb.runCommand(new Document("moveChunk", namespace)
          .append("find", new Document("first",
              prefix))
          .append("to", shardNames.get(y)));

      counter++;
    }
  }
}
