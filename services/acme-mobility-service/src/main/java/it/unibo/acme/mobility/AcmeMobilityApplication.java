package it.unibo.acme.mobility;

import io.camunda.zeebe.spring.client.EnableZeebeClient;
import io.camunda.zeebe.spring.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableZeebeClient // Abilita il client per Camunda 8
@Deployment(resources = "classpath:process.bpmn") // Deploy automatico del BPMN all'avvio
public class AcmeMobilityApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcmeMobilityApplication.class, args);
    }
}