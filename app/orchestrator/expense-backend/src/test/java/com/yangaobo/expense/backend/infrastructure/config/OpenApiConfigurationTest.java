package com.yangaobo.expense.backend.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

class OpenApiConfigurationTest {

    private final OpenApiConfiguration configuration =
            new OpenApiConfiguration();

    @Test
    void shouldDeclareJwtAndStandardErrorResponses() {
        OpenAPI openApi = configuration.expenseFlowOpenApi();
        Operation operation =
                new Operation().responses(new ApiResponses());
        openApi.setPaths(
                new Paths()
                        .addPathItem(
                                "/api/v1/example",
                                new PathItem().get(operation)));

        configuration.standardErrorResponses().customise(openApi);

        assertThat(openApi.getInfo().getTitle())
                .isEqualTo("ExpenseFlow API");
        assertThat(openApi.getComponents().getSecuritySchemes())
                .containsKey("bearerAuth");
        assertThat(openApi.getComponents().getSchemas())
                .containsKey("ApiErrorResponse");
        assertThat(operation.getResponses().keySet())
                .contains("401", "403", "409", "422", "503");
        assertThat(
                        operation
                                .getResponses()
                                .get("422")
                                .getContent()
                                .get("application/json")
                                .getSchema()
                                .get$ref())
                .isEqualTo(
                        "#/components/schemas/ApiErrorResponse");
    }
}
