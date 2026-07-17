package com.yangaobo.expense.audithistory.interfaces.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.audithistory.application.AuditHistoryService;
import com.yangaobo.expense.common.security.McpSecurityConfiguration;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(McpSecurityConfiguration.class)
public class AuditHistoryMcpConfiguration {

    @Bean
    McpJsonMapper auditMcpJsonMapper(ObjectMapper mapper) {
        return new JacksonMcpJsonMapper(mapper);
    }

    @Bean
    HttpServletStreamableServerTransportProvider auditMcpTransport(McpJsonMapper mapper) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(mapper).mcpEndpoint("/mcp")
                .keepAliveInterval(Duration.ofSeconds(30)).build();
    }

    @Bean
    ServletRegistrationBean<?> auditMcpServlet(
            HttpServletStreamableServerTransportProvider transport) {
        var registration = new ServletRegistrationBean<>(transport, "/mcp/*");
        registration.setName("audit-history-mcp");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean(destroyMethod = "close")
    McpSyncServer auditMcpServer(
            HttpServletStreamableServerTransportProvider transport,
            McpJsonMapper mapper,
            AuditHistoryService service,
            ObjectMapper objectMapper) {
        return McpServer.sync(transport)
                .serverInfo("my-expense-agent-audit-history-mcp", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .toolCall(tool(mapper, "check_duplicate_document", "按 SHA-256 检查重复票据，并可排除当前案例", duplicateSchema()),
                        (e,r)->result(objectMapper,service.checkDuplicateDocument(text(r,"sha256"),optionalUuid(r,"excludeCaseId"))))
                .toolCall(tool(mapper, "get_fund_reimbursement_history", "查询申请人历史经费报销记录", one("applicantId")),
                        (e,r)->result(objectMapper,service.getFundReimbursementHistory(text(r,"applicantId"))))
                .toolCall(tool(mapper, "save_review_evidence", "幂等保存审核证据", evidenceSchema()),
                        (e,r)->result(objectMapper,service.saveReviewEvidence(text(r,"requestId"),UUID.fromString(text(r,"caseId")),text(r,"evidenceType"),text(r,"contentHash"))))
                .toolCall(tool(mapper, "record_fund_reimbursement_history", "幂等写入已入账票据的经费报销历史", historySchema()),
                        (e,r)->result(objectMapper,service.recordFundReimbursementHistory(
                                text(r,"requestId"),UUID.fromString(text(r,"caseId")),text(r,"applicantId"),
                                text(r,"sellerName"),decimal(r,"amount"),text(r,"currency"),
                                LocalDate.parse(text(r,"expenseDate")),text(r,"documentSha256"))))
                .build();
    }

    private static McpSchema.Tool tool(McpJsonMapper m,String n,String d,String s){return McpSchema.Tool.builder().name(n).description(d).inputSchema(m,s).build();}
    private static String one(String n){return "{\"type\":\"object\",\"properties\":{\""+n+"\":{\"type\":\"string\"}},\"required\":[\""+n+"\"],\"additionalProperties\":false}";}
    private static String duplicateSchema(){return "{\"type\":\"object\",\"properties\":{\"sha256\":{\"type\":\"string\"},\"excludeCaseId\":{\"type\":\"string\"}},\"required\":[\"sha256\"],\"additionalProperties\":false}";}
    private static String evidenceSchema(){return "{\"type\":\"object\",\"properties\":{\"requestId\":{\"type\":\"string\"},\"caseId\":{\"type\":\"string\"},\"evidenceType\":{\"type\":\"string\"},\"contentHash\":{\"type\":\"string\"}},\"required\":[\"requestId\",\"caseId\",\"evidenceType\",\"contentHash\"],\"additionalProperties\":false}";}
    private static String historySchema(){return "{\"type\":\"object\",\"properties\":{\"requestId\":{\"type\":\"string\"},\"caseId\":{\"type\":\"string\"},\"applicantId\":{\"type\":\"string\"},\"sellerName\":{\"type\":\"string\"},\"amount\":{\"type\":\"number\"},\"currency\":{\"type\":\"string\"},\"expenseDate\":{\"type\":\"string\",\"format\":\"date\"},\"documentSha256\":{\"type\":\"string\"}},\"required\":[\"requestId\",\"caseId\",\"applicantId\",\"sellerName\",\"amount\",\"currency\",\"expenseDate\",\"documentSha256\"],\"additionalProperties\":false}";}
    private static String text(McpSchema.CallToolRequest r,String n){Object v=r.arguments().get(n);if(v==null||v.toString().isBlank())throw new IllegalArgumentException(n+"不能为空");return v.toString();}
    private static UUID optionalUuid(McpSchema.CallToolRequest r,String n){Object v=r.arguments().get(n);return v==null||v.toString().isBlank()?null:UUID.fromString(v.toString());}
    private static BigDecimal decimal(McpSchema.CallToolRequest r,String n){return new BigDecimal(text(r,n));}
    private static McpSchema.CallToolResult result(ObjectMapper m,Object v){try{return McpSchema.CallToolResult.builder().content(List.of(new McpSchema.TextContent(m.writeValueAsString(v)))).structuredContent(v).build();}catch(Exception e){throw new IllegalStateException(e);}}
}
