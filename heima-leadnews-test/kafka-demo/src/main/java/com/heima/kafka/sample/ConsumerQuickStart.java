package com.heima.kafka.sample;

import static org.apache.kafka.clients.consumer.ConsumerConfig.*;
import java.util.Properties;

public class ConsumerQuickStart {
    public static void main(String[] args) {
        Properties prop = new Properties();
        prop.put(BOOTSTRAP_SERVERS_CONFIG, "192.168.200.130:9092");
        prop.put(KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        prop.put(VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        // 设置消费者组

        // 2.创建消费者

        // 3.订阅主题

        // 4.拉去消息
    }
}
