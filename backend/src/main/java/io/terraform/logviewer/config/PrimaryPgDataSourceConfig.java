package io.terraform.logviewer.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import javax.sql.DataSource;

@Configuration
public class PrimaryPgDataSourceConfig {

    /** Берём spring.datasource.* (url, username, password, driver) */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties pgDataSourceProperties() {
        return new DataSourceProperties();
    }

    /** Из свойств строим HikariDataSource; имя бина ровно "dataSource" — так его возьмёт JPA */
    @Bean(name = "dataSource")
    @Primary
    @ConfigurationProperties("spring.datasource.hikari") // опционально: подтянет hikari-настройки, если есть
    public DataSource pgDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }
}
