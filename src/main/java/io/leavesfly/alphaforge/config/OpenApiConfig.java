package io.leavesfly.alphaforge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger 文档配置
 *
 * 自动生成 API 文档：
 * - JSON: /v3/api-docs
 * - Swagger UI: /swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI alphaForgeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AlphaForge API")
                        .description("AI大模型驱动的量化策略构建系统 - API 文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("AlphaForge")
                                .url("https://github.com/leavesfly/AlphaForge"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Bearer Token 认证")));
    }
}
