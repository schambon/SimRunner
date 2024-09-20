package org.schambon.loadsimrunner.runner.kafka;

import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.bson.types.ObjectId;
import org.schambon.loadsimrunner.WorkloadManager;
import org.schambon.loadsimrunner.report.Reporter;
import org.schambon.loadsimrunner.runner.AbstractRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaInsertRunner extends AbstractRunner {

    private static Logger LOGGER = LoggerFactory.getLogger(KafkaInsertRunner.class);

    private Producer<String, String> producer;
    private String topic;

    public KafkaInsertRunner(WorkloadManager config, Reporter reporter) {
        super(config, reporter);

        var props = new Properties();
        props.put("bootstrap.servers", params.get("bootstrap-servers"));
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        producer = new KafkaProducer<>(props);

        this.topic = params.getString("topic");
    }

    @Override
    protected long doRun() {
        var start = System.currentTimeMillis();
        var doc = template.generate();
        var rawId = doc.get("_id");
        if (rawId == null) rawId = new ObjectId();
        var id = rawId.toString();

        producer.send(new ProducerRecord<String,String>(topic, id, doc.toJson()));

        long duration = System.currentTimeMillis() - start;
        reporter.reportOp(name, 1, duration);

        return duration;
    }

}
