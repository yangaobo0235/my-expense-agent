package com.yangaobo.expense.service.interfaces.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.common.security.McpSecurityConfiguration;
import com.yangaobo.expense.service.application.ExpenseBusinessService;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(McpSecurityConfiguration.class)
public class ExpenseMcpConfiguration {

    @Bean
    McpJsonMapper expenseMcpJsonMapper(ObjectMapper mapper) {
        return new JacksonMcpJsonMapper(mapper);
    }

    @Bean
    HttpServletStreamableServerTransportProvider expenseMcpTransport(McpJsonMapper mapper) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(mapper).mcpEndpoint("/mcp")
                .keepAliveInterval(Duration.ofSeconds(30)).build();
    }

    @Bean
    ServletRegistrationBean<?> expenseMcpServlet(
            HttpServletStreamableServerTransportProvider transport) {
        var registration = new ServletRegistrationBean<>(transport, "/mcp/*");
        registration.setName("fund-reimbursement-mcp");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean(destroyMethod = "close")
    McpSyncServer expenseMcpServer(
            HttpServletStreamableServerTransportProvider transport,
            McpJsonMapper mapper,
            ExpenseBusinessService service,
            ObjectMapper objectMapper) {
        return McpServer.sync(transport)
                .serverInfo("my-expense-agent-business-mcp", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .toolCall(tool(mapper, "validate_invoice_number", "校验发票号码格式", invoiceSchema()),
                        (e, r) -> result(objectMapper, service.validateInvoiceNumber(text(r, "invoiceNumber"))))
                .toolCall(tool(mapper, "calculate_allowed_amount", "按制度限额计算可报销金额", amountSchema()),
                        (e, r) -> result(objectMapper, service.calculateAllowedAmount(decimal(r, "claimedAmount"), decimal(r, "policyLimit"))))
                .toolCall(tool(mapper, "submit_fund_reimbursement", "幂等登记审批通过的经费报销", reimbursementSchema()),
                        (e, r) -> result(objectMapper, service.submitReimbursement(text(r, "requestId"), UUID.fromString(text(r, "caseId")), decimal(r, "amount"), text(r, "currency"))))
                .toolCall(tool(mapper, "submit_fund_posting", "幂等提交审批通过的经费入账", paymentSchema()),
                        (e, r) -> result(objectMapper, service.submitFundPosting(text(r, "requestId"), UUID.fromString(text(r, "reimbursementId")), decimal(r, "amount"), text(r, "currency"))))
                .build();
    }

    private static McpSchema.Tool tool(McpJsonMapper mapper, String name, String desc, String schema) {
        return McpSchema.Tool.builder().name(name).description(desc).inputSchema(mapper, schema).build();
    }
    private static String invoiceSchema() { return "{\"type\":\"object\",\"properties\":{\"invoiceNumber\":{\"type\":\"string\"}},\"required\":[\"invoiceNumber\"],\"additionalProperties\":false}"; }
    private static String amountSchema() { return "{\"type\":\"object\",\"properties\":{\"claimedAmount\":{\"type\":\"number\"},\"policyLimit\":{\"type\":\"number\"}},\"required\":[\"claimedAmount\",\"policyLimit\"],\"additionalProperties\":false}"; }
    private static String reimbursementSchema() { return "{\"type\":\"object\",\"properties\":{\"requestId\":{\"type\":\"string\"},\"caseId\":{\"type\":\"string\"},\"amount\":{\"type\":\"number\"},\"currency\":{\"type\":\"string\"}},\"required\":[\"requestId\",\"caseId\",\"amount\",\"currency\"],\"additionalProperties\":false}"; }
    private static String paymentSchema() { return "{\"type\":\"object\",\"properties\":{\"requestId\":{\"type\":\"string\"},\"reimbursementId\":{\"type\":\"string\"},\"amount\":{\"type\":\"number\"},\"currency\":{\"type\":\"string\"}},\"required\":[\"requestId\",\"reimbursementId\",\"amount\",\"currency\"],\"additionalProperties\":false}"; }
    private static String text(McpSchema.CallToolRequest r, String n) { Object v=r.arguments().get(n); if(v==null||v.toString().isBlank()) throw new IllegalArgumentException(n+"不能为空"); return v.toString(); }
    private static BigDecimal decimal(McpSchema.CallToolRequest r, String n) { return new BigDecimal(text(r,n)); }
    private static McpSchema.CallToolResult result(ObjectMapper mapper,Object value){try{return McpSchema.CallToolResult.builder().content(List.of(new McpSchema.TextContent(mapper.writeValueAsString(value)))).structuredContent(value).build();}catch(Exception ex){throw new IllegalStateException(ex);}}
}
