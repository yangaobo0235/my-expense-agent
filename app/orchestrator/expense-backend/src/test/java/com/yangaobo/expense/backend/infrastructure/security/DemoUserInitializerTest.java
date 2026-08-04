package com.yangaobo.expense.backend.infrastructure.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DemoUserInitializerTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private JdbcClient jdbcClient;

    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationArguments arguments;

    @Test
    void createsAllOptInDemoUsersWithTheirSingleRole() {
        when(jdbcClient
                        .sql(startsWith("SELECT EXISTS"))
                        .param(eq("username"), any())
                        .query(Boolean.class)
                        .single())
                .thenReturn(false);
        when(passwordEncoder.encode(anyString()))
                .thenAnswer(invocation -> "encoded-" + invocation.getArgument(0, String.class));

        new DemoUserInitializer(jdbcClient, passwordEncoder).run(arguments);

        verify(passwordEncoder).encode("student");
        verify(passwordEncoder).encode("advisor");
        verify(passwordEncoder).encode("college_reviewer");
        verify(passwordEncoder).encode("finance_admin");
        verify(passwordEncoder).encode("auditor");
        verify(jdbcClient, times(5)).sql(contains("INSERT INTO auth_user ("));
        verify(jdbcClient, times(5)).sql(contains("INSERT INTO auth_user_role"));
    }
}
