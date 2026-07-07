package com.yangaobo.expense.backend.infrastructure.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.agents.mcp.ExpenseMcpClientFactory;
import com.yangaobo.expense.agents.mcp.ExpenseMcpGateway;
import com.yangaobo.expense.backend.application.governance.AgentInputGuard;
import com.yangaobo.expense.backend.application.governance.DependencyCircuitBreaker;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.settlement.ApprovedExpenseWriter;
import com.yangaobo.expense.backend.application.workflow.ExpenseContextGateway;
import com.yangaobo.expense.backend.application.workflow.WorkflowEvidenceGateway;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExpenseMcpClientProperties.class)
@ConditionalOnProperty(
        prefix = "expense.mcp.client",
        name = "enabled",
        havingValue = "true")
public class ExpenseMcpClientConfiguration {

    @Bean
    ClientCredentialsTokenProvider mcpAccessTokenProvider(
            RestClient.Builder restClientBuilder,
            ExpenseMcpClientProperties properties,
            Clock clock) {
        return new ClientCredentialsTokenProvider(
                restClientBuilder.build(), properties, clock);
    }

    @Bean
    McpRetryExecutor mcpRetryExecutor(
            ExpenseMcpClientProperties properties,
            DependencyCircuitBreaker circuitBreaker) {
        return new McpRetryExecutor(
                properties.maxAttempts(), properties.retryDelay(), circuitBreaker);
    }

    @Bean(destroyMethod = "close")
    @Lazy
    ExpenseMcpGateway expenseMcpGateway(
            ClientCredentialsTokenProvider tokenProvider,
            ExpenseMcpClientProperties properties) {
        McpClient account =
                create(
                        "account",
                        properties.accountUrl(),
                        tokenProvider,
                        properties);
        McpClient expense =
                create(
                        "expense",
                        properties.expenseUrl(),
                        tokenProvider,
                        properties);
        McpClient auditHistory =
                create(
                        "audit-history",
                        properties.auditHistoryUrl(),
                        tokenProvider,
                        properties);
        return new ExpenseMcpGateway(
                List.of(account, expense, auditHistory));
    }

    @Bean
    @Lazy
    ToolProvider expenseReadOnlyMcpToolProvider(
            @Lazy ExpenseMcpGateway gateway) {
        return gateway.readOnlyToolProvider();
    }

    @Bean
    ApprovedMcpWriteService approvedMcpWriteService(
            @Lazy ExpenseMcpGateway gateway,
            ExpenseCaseApplicationService caseService,
            ObjectMapper objectMapper,
            AgentInputGuard inputGuard,
            McpRetryExecutor retryExecutor) {
        return new ApprovedMcpWriteService(
                gateway, caseService, objectMapper, inputGuard, retryExecutor);
    }

    @Bean
    ExpenseContextGateway mcpExpenseContextGateway(
            @Lazy ExpenseMcpGateway gateway,
            ObjectMapper objectMapper,
            AgentInputGuard inputGuard,
            McpRetryExecutor retryExecutor) {
        return new McpExpenseContextGateway(
                gateway, objectMapper, inputGuard, retryExecutor);
    }

    @Bean
    WorkflowEvidenceGateway mcpWorkflowEvidenceGateway(
            ApprovedMcpWriteService writeService) {
        return new McpWorkflowEvidenceGateway(writeService);
    }

    @Bean
    ApprovedExpenseWriter mcpApprovedExpenseWriter(
            ApprovedMcpWriteService writeService,
            ObjectMapper objectMapper) {
        return new McpApprovedExpenseWriter(writeService, objectMapper);
    }

    private static McpClient create(
            String key,
            String url,
            ClientCredentialsTokenProvider tokenProvider,
            ExpenseMcpClientProperties properties) {
        return ExpenseMcpClientFactory.create(
                key,
                url,
                tokenProvider::accessToken,
                properties.connectionTimeout(),
                properties.toolTimeout());
    }
}
