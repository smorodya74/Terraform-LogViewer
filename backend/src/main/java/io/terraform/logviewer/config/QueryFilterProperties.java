package io.terraform.logviewer.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.query-filter")
public class QueryFilterProperties {

    private boolean enabled = false;

    private Map<String, String> forcedFilters = new LinkedHashMap<>();

    private List<String> blockedFields = new ArrayList<>();
}
