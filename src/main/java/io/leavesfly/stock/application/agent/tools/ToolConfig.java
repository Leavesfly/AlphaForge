package io.leavesfly.stock.application.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具配置类
 *
 * 在Spring容器启动时，自动收集所有Tool Bean并注册到ToolRegistry中。
 * 参考TinyClaw在CliCommand中逐个注册工具的方式，但利用Spring的自动收集机制更简洁。
 *
 * 新增工具只需创建实现Tool接口的@Component类，无需修改此类。
 */
@Component
public class ToolConfig {

    private static final Logger log = LoggerFactory.getLogger(ToolConfig.class);

    public ToolConfig(ToolRegistry toolRegistry,
                      @Autowired(required = false) List<Tool> registeredTools) {
        if (registeredTools != null && !registeredTools.isEmpty()) {
            for (Tool tool : registeredTools) {
                toolRegistry.register(tool);
            }
            log.info("ToolRegistry已注册 {} 个工具: {}", registeredTools.size(), toolRegistry.list());
        } else {
            log.warn("未发现任何Tool Bean，ToolRegistry为空");
        }
    }
}
