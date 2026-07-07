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
import java.util.List;
import java.util.Map;
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
                .serverInfo("expense-account-mcp", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .toolCall(
                        tool(mapper, "get_employee_profile", "查询员工、部门、等级和地区信息"),
                        (exchange, request) -> result(objectMapper, service.getEmployeeProfile(required(request, "employeeId"))))
                .toolCall(
                        tool(mapper, "get_payment_methods", "查询员工可用支付方式"),
                        (exchange, request) -> result(objectMapper, service.getPaymentMethods(required(request, "employeeId"))))
                .toolCall(
                        tool(mapper, "get_account_balance", "查询员工费用账户可用余额"),
                        (exchange, request) -> result(objectMapper, service.getAccountBalance(required(request, "employeeId"))))
                .build();
    }

    private static McpSchema.Tool tool(McpJsonMapper mapper, String name, String description) {
        return McpSchema.Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(
                        mapper,
                        """
                        {"type":"object","properties":{"employeeId":{"type":"string"}},
                         "required":["employeeId"],"additionalProperties":false}
                        """)
                .build();
    }

    private static String required(McpSchema.CallToolRequest request, String name) {
        Object value = request.arguments().get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return text.trim();
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
