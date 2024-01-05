package org.schambon.loadsimrunner.client;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.stream.FileImageInputStream;

import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.schambon.loadsimrunner.errors.InvalidConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.AutoEncryptionSettings;
import com.mongodb.ClientEncryptionSettings;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.CreateEncryptedCollectionParams;
import com.mongodb.client.vault.ClientEncryption;
import com.mongodb.client.vault.ClientEncryptions;

/**
 * Get 
 */
public class MongoClientHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoClientHelper.class);
    
    public static void doInTemporaryClient(String uri, TaskWithClient task) {

        try (var client = MongoClients.create(uri)) {
            task.run(client);
        }

    }

    public static interface TaskWithClient {
        void run(MongoClient client);
    }

    public static MongoClient client(String uri, Document encryption) {
        
        if (! isOn(encryption)) {
            return MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build());
            
        } else {
            var cryptSharedLibPath = getOrEnv(encryption, "sharedlib");
            var extraOptions = new HashMap<String, Object>();
            extraOptions.put("cryptSharedLibPath", cryptSharedLibPath);

            var kmsProviderCredentials = kmsProviderCredentials((Document) encryption.get("keyProviders"));

            var autoEncryptionSettings = AutoEncryptionSettings.builder()
                .keyVaultNamespace((String) getOrEnv(encryption, "keyVaultNamespace"))
                .kmsProviders(kmsProviderCredentials)
                .extraOptions(extraOptions)
                .build();

            var clientSettings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .autoEncryptionSettings(autoEncryptionSettings)
                .build();

            var encryptedClient = MongoClients.create(clientSettings);

            var keyVaultUri = encryption.getString("keyVaultUri");
            if (keyVaultUri == null) {
                keyVaultUri = uri;
            }

            var clientEncryptionSettings = ClientEncryptionSettings.builder()
                .keyVaultMongoClientSettings(MongoClientSettings.builder().applyConnectionString(new ConnectionString(keyVaultUri)).build())
                .keyVaultNamespace(encryption.getString("keyVaultNamespace"))
                .kmsProviders(kmsProviderCredentials)
                .build();
            var clientEncryption = ClientEncryptions.create(clientEncryptionSettings);

            for (var coll : encryption.getList("collections", Document.class)) {
                createEncryptedCollection(encryptedClient, clientEncryption, coll);
            }

            return encryptedClient;
        }

    }

    private static void createEncryptedCollection(MongoClient encryptedClient, ClientEncryption clientEncryption,
            Document collDef) {

                
        String database = collDef.getString("database");
        String collection = collDef.getString("collection");
        String kmsProvider = collDef.getString("kmsProvider");
        var fields = (List) collDef.get("fields");

        if (database == null || collection == null || kmsProvider == null || fields == null) {
            throw new InvalidConfigException("Encrypted collection mandatory fields are database, collection, kmsProvider, fields");
        }

        var db = encryptedClient.getDatabase(database);
        if (collExists(db, collection)) {
            LOGGER.info("Collection {}.{} already exists, do not create encrypted collection", database, collection);
            return;
        }

        if (! collDef.containsKey("fields")) {
            throw new InvalidConfigException(String.format("Encrypted collection %s does not have a field map", collection));
        }

        Document fieldMap = new Document();
        fieldMap.put("fields", fields);

        CreateCollectionOptions opts = new CreateCollectionOptions().encryptedFields(fieldMap);
        CreateEncryptedCollectionParams params = new CreateEncryptedCollectionParams(kmsProvider);
        params.masterKey(new BsonDocument());  // TODO this will need to change for cloud KMS or KMIP


        try {
            clientEncryption.createEncryptedCollection(db, collection, opts, params);
        } catch (Exception e) {
            throw new RuntimeException("Cannot initialise encrypted collection", e);
        }
    }

    public static boolean collExists(MongoDatabase db, String collection) {
        var found = false;
        for (var name : db.listCollectionNames()) {
            if (name.equals(collection)) {
                found = true;
                break;
            }
        }
        return found;
    }

    private static Map<String, Map<String, Object>> kmsProviderCredentials(Document config) {

        Map<String, Map<String, Object>> result = new HashMap<>();

        for (var entry : config.entrySet()) {
            switch (entry.getKey()) {
                case "local":
                    result.put(entry.getKey(), _localKmsProviderCreds((Document) entry.getValue()));
                    break;
                default:
                    throw new UnsupportedOperationException(String.format("Key provider %s is not supported", entry.getKey()));
            }
        }

        return result;
    }

    private static Map<String, Object> _localKmsProviderCreds(Document config) {
        var keyPath = Paths.get(config.getString("key"));

        try (var fis = new FileImageInputStream(keyPath.toFile())) {
            byte[] cmk = new byte[96];
            if (fis.read(cmk) < 96) {
                throw new RuntimeException("Encryption keyfile should be 96 bytes");
            }

            Map<String, Object> keyMap = new HashMap<>();
            keyMap.put("key", cmk);
            return keyMap;
        } catch(IOException ioe) {
            throw new RuntimeException("Cannot read encryption keyfile", ioe);
        }
 
    }

    public static boolean isOn(Document config) {
        if (config == null) return false;
        else return config.getBoolean("enabled", false);
    }

    private static Object getOrEnv(Document doc, String key) {
        if (doc == null || !doc.containsKey(key)) {
            return null;
        } else {
            var found = doc.get(key);
            if (found instanceof String) {
                var string = (String) found;
                if (string.startsWith("$")) {
                    return System.getenv(string.substring(1));
                } else return string;
            } else {
                return found;
            }
        }
    }

}
