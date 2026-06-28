package io.leavesfly.alphaforge;

import io.leavesfly.alphaforge.application.strategy.StrategyCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.datasource.url=jdbc:sqlite::memory:",
        "app.home=${java.io.tmpdir}/j-stock-test"
})
@DisplayName("Spring Boot 集成测试")
class ApplicationIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private StrategyCatalog strategyCatalog;

    @Test
    @DisplayName("应用上下文应成功启动")
    void contextLoads() throws Exception {
        assertNotNull(dataSource);
        assertTrue(dataSource.getConnection().isValid(2));
    }

    @Test
    @DisplayName("策略目录应已加载")
    void strategiesLoaded() {
        assertFalse(strategyCatalog.listAll().isEmpty());
    }
}
