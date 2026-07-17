package com.yangaobo.expense.backend.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI campusFundFlowOpenApi() {
        Components components =
                new Components()
                        .addSecuritySchemes(
                                BEARER_AUTH,
                                        new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                        .bearerFormat("JWT"));
        return new OpenAPI()
                .info(
                        new Info()
                                .title("MyExpenseAgent API")
                                .version("v1")
                                .description(
                                        "校园项目经费报销、合规审核、人工复核和审批后入账 API"))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    OpenApiCustomizer standardErrorResponses() {
        return openApi -> {
            ensureApiErrorResponseSchema(openApi);
            if (openApi.getPaths() == null) {
                return;
            }
                openApi.getPaths()
                        .values()
                        .forEach(
                                path ->
                                        path.readOperations()
                                                .forEach(
                                                        operation -> {
                                                            add(
                                                                    operation
                                                                            .getResponses(),
                                                                    "401",
                                                                    "Bearer Token 缺失或无效",
                                                                    "ACCESS_DENIED");
                                                            add(
                                                                    operation
                                                                            .getResponses(),
                                                                    "403",
                                                                    "当前身份没有访问权限",
                                                                    "ACCESS_DENIED");
                                                            add(
                                                                    operation
                                                                            .getResponses(),
                                                                    "409",
                                                                    "状态冲突、乐观锁冲突或重复请求",
                                                                    "INVALID_STATE_TRANSITION");
                                                            add(
                                                                    operation
                                                                            .getResponses(),
                                                                    "422",
                                                                    "请求或业务字段校验失败",
                                                                    "VALIDATION_FAILED");
                                                            add(
                                                                    operation
                                                                            .getResponses(),
                                                                    "503",
                                                                    "外部依赖暂时不可用",
                                                                    "DEPENDENCY_UNAVAILABLE");
                                                        }));
        };
    }

    private static void ensureApiErrorResponseSchema(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        openApi.getComponents()
                .addSchemas(
                        "ApiErrorResponse",
                        new ObjectSchema()
                                .addProperty("code", new StringSchema())
                                .addProperty("message", new StringSchema())
                                .addProperty("requestId", new StringSchema())
                                .addProperty("details", new ObjectSchema()));
    }

    private static void add(
            io.swagger.v3.oas.models.responses.ApiResponses responses,
            String status,
            String description,
            String code) {
        if (responses.containsKey(status)) {
            return;
        }
        Schema<?> schema =
                new Schema<>()
                        .$ref("#/components/schemas/ApiErrorResponse");
        io.swagger.v3.oas.models.examples.Example example =
                new io.swagger.v3.oas.models.examples.Example()
                        .value(
                                Map.of(
                                        "code",
                                        code,
                                        "message",
                                        description,
                                        "requestId",
                                        "550e8400-e29b-41d4-a716-446655440000",
                                        "details",
                                        Map.of()));
        responses.addApiResponse(
                status,
                new ApiResponse()
                        .description(description)
                        .content(
                                new Content()
                                        .addMediaType(
                                                "application/json",
                                                new MediaType()
                                                        .schema(schema)
                                                        .addExamples(
                                                                "default",
                                                                example))));
    }
}
