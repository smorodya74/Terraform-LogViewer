package io.terraform.logviewer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "app.plugins.enabled=false",
        "app.clickhouse.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("h2")
class TerraformLogViewerApplicationTests {

    @Test
    void contextLoads() {
    }

    @TestConfiguration
    static class JacksonFallback {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

}
