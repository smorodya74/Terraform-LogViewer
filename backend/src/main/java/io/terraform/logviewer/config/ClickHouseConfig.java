package io.terraform.logviewer.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

@Configuration
public class ClickHouseConfig {

    /**
     * Создаём DataSource для ClickHouse только если app.clickhouse.enabled=true.
     * Если включили, но url пустой — даём понятную ошибка-конфигурацию.
     */
    @Bean(name = "clickHouseDataSource")
    @ConditionalOnProperty(prefix = "app.clickhouse", name = "enabled", havingValue = "true")
    public DataSource clickHouseDataSource(@Value("${app.clickhouse.url:}") String url,
                                           @Value("${app.clickhouse.user:default}") String user,
                                           @Value("${app.clickhouse.password:}") String pass) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("app.clickhouse.enabled=true, но app.clickhouse.url пуст. Укажи jdbc:clickhouse://host:8123/db");
        }
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", pass);
        try {
            return new ClickHouseDataSource(url, props);
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось инициализировать ClickHouse DataSource", ex);
        }
    }

    @Bean(name = "clickHouseJdbcTemplate")
    @ConditionalOnBean(name = "clickHouseDataSource")
    public JdbcTemplate clickHouseJdbcTemplate(@Qualifier("clickHouseDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
