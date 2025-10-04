package io.terraform.logviewer.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация gRPC-плагинов (app.plugins.*).
 */
@ConfigurationProperties(prefix = "app.plugins")
public class PluginProperties {

    /**
     * Включение/выключение механизма плагинов.
     */
    private boolean enabled = false;

    /**
     * Дедлайн для выполнения вызова плагина.
     */
    private Duration deadline = Duration.ofSeconds(5);

    /**
     * Список плагинов.
     */
    private List<PluginConfig> plugins = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getDeadline() {
        return deadline;
    }

    public void setDeadline(Duration deadline) {
        this.deadline = deadline;
    }

    public List<PluginConfig> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<PluginConfig> plugins) {
        this.plugins = plugins;
    }

    /**
     * Конфигурация одного плагина.
     */
    public static class PluginConfig {
        private String id;
        private String host = "localhost";
        private int port = 50051;
        private boolean plaintext = true;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isPlaintext() {
            return plaintext;
        }

        public void setPlaintext(boolean plaintext) {
            this.plaintext = plaintext;
        }
    }
}
