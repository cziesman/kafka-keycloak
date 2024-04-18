package com.redhat.kafka.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class KafkaClientApplication {

    public static void main(String[] args) {

        SpringApplication.run(KafkaClientApplication.class, args);
    }

}
