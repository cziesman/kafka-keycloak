package com.redhat.kafka.client.controller;

import java.util.List;

import com.redhat.kafka.client.service.KafkaConsumer;
import com.redhat.kafka.client.service.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/web")
public class ConsumerController {

    @Value("${kafka.topic.name}")
    private String topicName;

    @Autowired
    private KafkaConsumer kafkaConsumer;

    @GetMapping({
            "/index", "/", ""
    })
    public String index() {

        return "redirect:/web/list";
    }

    @GetMapping(value = "/list")
    public String list(Model model) {

        model.addAttribute("messages", kafkaConsumer.getMessages());

        return "message-list";
    }

    @GetMapping(value = "/list-json")
    @ResponseBody
    public Wrapper listAsJson() {

        return new Wrapper(kafkaConsumer.getMessages());
    }

    /*
     * This class is needed because Datatables expects the response to be a JSON object
     * named "data," which in turn contains the list of messages.
     */
    static class Wrapper {

        private final List<Message> data;

        public Wrapper(List<Message> data) {

            this.data = data;
        }

        public List<Message> getData() {

            return data;
        }

    }

}
