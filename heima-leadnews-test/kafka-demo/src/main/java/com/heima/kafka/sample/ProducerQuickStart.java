package com.heima.kafka.sample;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Properties;

import static org.apache.kafka.clients.producer.ProducerConfig.*;

public class ProducerQuickStart {
    public static void main(String[] args) {
        // 1.kafka连接配置信息
        Properties prop = new Properties();
        prop.put(BOOTSTRAP_SERVERS_CONFIG, "192.168.200.130:9092");
        prop.put(KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        prop.put(VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        // 2.创建kafka生产者对象
        KafkaProducer<String, String> producer = new KafkaProducer<>(prop);
        // 3.发送消息
        // 参数: topic, 消息的key, 消息的value
        ProducerRecord<String, String> kvProducerRecord = new ProducerRecord<>("topic-first", "key-001", "hello kafka");
        producer.send(kvProducerRecord);
        // 4.关闭消息通道 必须要关闭 否则消息发送不成功
        producer.close();
    }
}
