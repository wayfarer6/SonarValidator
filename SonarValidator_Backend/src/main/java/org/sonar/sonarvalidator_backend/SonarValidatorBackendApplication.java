package org.sonar.sonarvalidator_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SonarValidatorBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SonarValidatorBackendApplication.class, args);
    }

}
