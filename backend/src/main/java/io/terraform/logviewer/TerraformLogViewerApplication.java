package io.terraform.logviewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TerraformLogViewerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TerraformLogViewerApplication.class, args);
    }
}
