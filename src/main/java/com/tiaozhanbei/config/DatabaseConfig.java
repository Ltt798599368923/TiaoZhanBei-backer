package com.tiaozhanbei.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

@Configuration
public class DatabaseConfig {
    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${DATABASE_URL:}") String databaseUrl,
            @Value("${DB_HOST:localhost}") String databaseHost,
            @Value("${DB_PORT:5432}") String databasePort,
            @Value("${DB_NAME:tiaozhanbei}") String databaseName,
            @Value("${DB_USERNAME:postgres}") String username,
            @Value("${DB_PASSWORD:postgres}") String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(resolveJdbcUrl(databaseUrl, databaseHost, databasePort, databaseName));
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName("org.postgresql.Driver");
        return dataSource;
    }

    private String resolveJdbcUrl(String databaseUrl, String host, String port, String database) {
        if (databaseUrl == null || databaseUrl.trim().isEmpty()) {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database;
        }

        String normalized = databaseUrl.trim().replaceFirst("^jdbc:", "");
        try {
            URI uri = new URI(normalized);
            if (!"postgresql".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("DATABASE_URL must be a PostgreSQL connection URL");
            }
            String portPart = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            return "jdbc:postgresql://" + uri.getHost() + portPart + path + query;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("DATABASE_URL is invalid", e);
        }
    }
}
