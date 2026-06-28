package io.leavesfly.alphaforge.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;

/**
 * SQLite 数据源配置：使用 Druid 连接池，启用外键约束。
 */
@Configuration
public class SqliteDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.druid")
    public DataSource dataSource(DataSourceProperties properties) {
        DruidDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(DruidDataSource.class)
                .build();
        dataSource.setConnectionInitSqls(Collections.singletonList("PRAGMA foreign_keys=ON"));
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
