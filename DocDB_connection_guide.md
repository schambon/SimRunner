# Enhanced Database Connection Support

SimRunner now supports connecting to both MongoDB Atlas and Amazon DocumentDB with the enhanced MongoClientHelper.

## Connection Types Supported

### 1. MongoDB Atlas (SRV Records)
Use the standard `mongodb+srv://` connection string format for MongoDB Atlas clusters.

**Configuration Example:**
```json
{
    "connectionString": "mongodb+srv://username:password@cluster0.abcde.mongodb.net",
    "templates": [...]
}
```

### 2. Amazon DocumentDB
Use the standard `mongodb://` connection string format for Amazon DocumentDB clusters.

**Configuration Options:**

#### Option 1: Using database configuration block (Recommended)
```json
{
    "connectionString": "mongodb://username:password@docdb-cluster.cluster-xyz.us-east-1.docdb.amazonaws.com:27017",
    "tlsOptions": {
        "tls": true,
        "tlsCAFile": "/path/to/rds-ca-2019-root.pem",
        "invalidHostNameAllowed": false
    },
    "templates": [...]
}
```

## Database Configuration Parameters

When connecting to DocumentDB or other databases requiring custom TLS settings, you can use the `tlsOptions` configuration block:

| Parameter | Type | Description | Default |
|-----------|------|-------------|---------|
| `tls` | boolean | Enable TLS/SSL connection | `false` |
| `tlsCAFile` | string | Path to the TLS Certificate Authority file | `null` |
| `invalidHostNameAllowed` | boolean | Allow connections to servers with invalid hostnames | `false` |

## DocumentDB Setup Guide

### Prerequisites
1. Download the Amazon DocumentDB Certificate Authority (CA) file:
   ```bash
   wget https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem
   ```

2. Place the certificate file in a secure location on your system.

### Connection String Format
For DocumentDB, use the following format:
```
mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database][?options]]
```

### Example DocumentDB Connection
```json
{
    "connectionString": "mongodb://myuser:mypassword@my-docdb-cluster.cluster-abc123.us-east-1.docdb.amazonaws.com:27017",
    "tlsOptions": {
        "tls": true,
        "tlsCAFile": "/opt/certs/global-bundle.pem",
        "invalidHostNameAllowed": false
    },
    "reportInterval": 10000,
    "templates": [
        {
            "name": "test_collection",
            "database": "testdb",
            "collection": "documents",
            "drop": true,
            "instances": 1000,
            "template": {
                "_id": "%objectid",
                "name": "%name.fullName",
                "timestamp": "%now"
            }, ...
        }
    ]
}
```

## Automatic Connection Detection

The enhanced MongoClientHelper automatically detects the connection type:

- **MongoDB Atlas**: Detected by `mongodb+srv://` protocol
- **DocumentDB**: Detected by `mongodb://` protocol with DocumentDB-specific hostnames (containing "docdb", "documentdb", or "amazonaws.com")

## Migration from Original MongoClientHelper

The EnhancedMongoClientHelper is fully backward compatible with existing configurations. No changes are required for existing MongoDB Atlas connections.

To enable DocumentDB support for existing configurations:
1. Change connection string from `mongodb+srv://` to `mongodb://` format
2. Add the `tlsOptions` configuration block with TLS settings
3. Ensure the DocumentDB CA certificate is available

## Error Handling

Common connection issues and solutions:

### SSL/TLS Certificate Issues
- Ensure the CA certificate file path is correct and accessible
- Verify the certificate file is the correct one for your DocumentDB region

### Connection Timeout
- Check network connectivity to DocumentDB cluster
- Verify security group settings allow connections on port 27017
- Ensure VPC/subnet configuration is correct

### Authentication Failures
- Verify username and password are correct
- Check that the user has appropriate permissions for the target database

## Environment Variables

You can use environment variables in the configuration by prefixing with `$`:

```json
{
    "connectionString": "$DOCDB_CONNECTION_STRING",
    "tlsOptions": {
        "tls": true,
        "tlsCAFile": "$DOCDB_CA_FILE_PATH"
    }
}
```

Set the environment variables:
```bash
export DOCDB_CONNECTION_STRING="mongodb://user:pass@cluster.amazonaws.com:27017"
export DOCDB_CA_FILE_PATH="/opt/certs/rds-ca-2019-root.pem"
```