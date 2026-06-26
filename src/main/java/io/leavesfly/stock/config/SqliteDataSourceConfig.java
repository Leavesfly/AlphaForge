package io.leavesfly.stock.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 数据源配置：启用外键约束。
 */
@Configuration
public class SqliteDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties properties) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setConnectionInitSql("PRAGMA foreign_keys=ON");
        return dataSource;
    }

    /** 启动后再次确保外键开启（兼容已有连接池） */
    @Bean
    public SqliteForeignKeyInitializer sqliteForeignKeyInitializer(DataSource dataSource) {
        return new SqliteForeignKeyInitializer(dataSource);
    }

    static class SqliteForeignKeyInitializer {
        SqliteForeignKeyInitializer(DataSource dataSource) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys=ON");
            } catch (SQLException e) {
                throw new IllegalStateException("无法启用 SQLite 外键约束", e);
            }
        }
    }
}
