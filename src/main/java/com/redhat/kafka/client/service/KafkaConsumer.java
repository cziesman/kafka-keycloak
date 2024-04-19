package com.redhat.kafka.client.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.listener.AbstractConsumerSeekAware;
import org.springframework.stereotype.Component;

@Component
public class KafkaConsumer extends AbstractConsumerSeekAware {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumer.class);

    private final List<Message> messages = new ArrayList<>();

    @PostConstruct
    public void initialize() {

        seekToTimestamp(System.currentTimeMillis() - 120000L);
    }

    @KafkaListener(id = "KafkaConsumer", autoStartup = "true", topics = {"${kafka.topic.name}"},
            topicPartitions = @TopicPartition(topic = "${kafka.topic.name}", partitions = "0-9"))
    public void listen(String message) {

        LOG.info(message);

        Message incoming = new Message();
        incoming.setText(message);
        incoming.setTimestamp(ZonedDateTime.now());
        messages.add(incoming);
    }

    public List<Message> getMessages() {

        Collections.sort(messages, Comparator.comparing(Message::getTimestamp));
        Collections.reverse(messages);
        return Collections.unmodifiableList(messages);
    }

}
