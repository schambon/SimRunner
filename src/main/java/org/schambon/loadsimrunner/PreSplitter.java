package org.schambon.loadsimrunner;

import com.mongodb.client.MongoClient;

public interface PreSplitter {
  void preSplit(MongoClient client, String namespace);
}
