package com.yangaobo.expense.account.interfaces.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.account.application.AccountApplicationService;
import com.yangaobo.expense.common.security.McpSecurityConfiguration;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(McpSecurityConfiguration.class)
public class AccountMcpConfiguration {

    @Bean
    McpJsonMapper accountMcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper);
    }

    @Bean
    HttpServletStreamableServerTransportProvider accountMcpTransport(McpJsonMapper mapper) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(mapper)
                .mcpEndpoint("/mcp")
                .keepAliveInterval(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    ServletRegistrationBean<?> accountMcpServlet(
            HttpServletStreamableServerTransportProvider transport) {
        ServletRegistrationBean<?> registration = new ServletRegistrationBean<>(transport, "/mcp/*");
        registration.setName("account-mcp");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean(destroyMethod = "close")
    McpSyncServer accountMcpServer(
            HttpServletStreamableServerTransportProvider transport,
            McpJsonMapper mapper,
            AccountApplicationService service,
            ObjectMapper objectMapper) {
        return McpServer.sync(transport)
                .serverInfo("my-expense-agent-account-mcp", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .toolCall(
                        tool(mapper, "get_applicant_profile", "查询学生申请人、所属项目、校园角色和地区信息", applicantSchema()),
                        (exchange, request) -> result(objectMapper, service.getApplicantProfile(required(request, "applicantId"), optional(request, "projectCode"))))
                .toolCall(
                        tool(mapper, "get_reimbursement_accounts", "查询申请人可用的报销入账账户", one("applicantId")),
                        (exchange, request) -> result(objectMapper, service.getReimbursementAccounts(required(request, "applicantId"))))
                .toolCall(
                        tool(mapper, "get_project_budget_balance", "查询申请人所属项目的共享经费预算", applicantSchema()),
                        (exchange, request) -> result(objectMapper, service.getProjectBudgetBalance(required(request, "applicantId"), optional(request, "projectCode"))))
                .toolCall(
                        tool(mapper, "debit_project_budget", "幂等扣减审批通过申请对应的共享项目预算", debitSchema()),
                        (exchange, request) -> result(objectMapper, service.debitProjectBudget(
                                required(request, "requestId"),
                                UUID.fromString(required(request, "caseId")),
                                required(request, "projectCode"),
                                required(request, "applicantId"),
                                decimal(request, "amount"),
                                required(request, "currency"))))
                .build();
    }

    private static McpSchema.Tool tool(
            McpJsonMapper mapper, String name, String description, String schema) {
        return McpSchema.Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(mapper, schema)
                .build();
    }

    private static String one(String name) {
        return "{\"type\":\"object\",\"properties\":{\"" + name
                + "\":{\"type\":\"string\"}},\"required\":[\"" + name
                + "\"],\"additionalProperties\":false}";
    }

    private static String applicantSchema() {
        return """
                {"type":"object","properties":{
                  "applicantId":{"type":"string"},"projectCode":{"type":"string"}},
                 "required":["applicantId"],"additionalProperties":false}
                """;
    }

    private static String debitSchema() {
        return """
                {"type":"object","properties":{
                  "requestId":{"type":"string"},"caseId":{"type":"string"},
                  "projectCode":{"type":"string"},"applicantId":{"type":"string"},
                  "amount":{"type":"number"},"currency":{"type":"string"}},
                 "required":["requestId","caseId","projectCode","applicantId","amount","currency"],
                 "additionalProperties":false}
                """;
    }

    private static String required(McpSchema.CallToolRequest request, String name) {
        Object value = request.arguments().get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return text.trim();
    }

    private static String optional(McpSchema.CallToolRequest request, String name) {
        Object value = request.arguments().get(name);
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    static BigDecimal decimal(McpSchema.CallToolRequest request, String name) {
        Object value = request.arguments().get(name);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + "必须是合法金额", exception);
        }
    }

    private static McpSchema.CallToolResult result(ObjectMapper mapper, Object value) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(write(mapper, value))))
                .structuredContent(value)
                .build();
    }

    private static String write(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("MCP 结果序列化失败", exception);
        }
    }
}
