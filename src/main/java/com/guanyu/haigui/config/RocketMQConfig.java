package com.guanyu.haigui.config;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RocketMQConfig {

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Bean
    public RocketMQTemplate rocketMQTemplate() {
        RocketMQTemplate template = new RocketMQTemplate();
        template.setProducer(new org.apache.rocketmq.client.producer.DefaultMQProducer("DEFAULT_PRODUCER_GROUP"));
        template.getProducer().setNamesrvAddr(nameServer);
        return template;
    }
}