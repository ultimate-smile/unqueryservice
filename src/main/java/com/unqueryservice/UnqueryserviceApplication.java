package com.unqueryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UnqueryserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UnqueryserviceApplication.class, args);
    }
}
