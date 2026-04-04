package com.datanote.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) 文档配置
 */
@Configuration
public class OpenApiConfig {

    /**
     * 自定义 OpenAPI 文档信息
     *
     * @return OpenAPI 配置
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DataNote API")
                        .version("1.0")
                        .description("DataNote 数据开发平台 API 文档"));
    }
}
