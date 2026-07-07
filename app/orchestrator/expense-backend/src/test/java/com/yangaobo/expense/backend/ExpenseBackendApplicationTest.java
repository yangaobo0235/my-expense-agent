package com.yangaobo.expense.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.yangaobo.expense.backend.interfaces.rest.SystemController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(SystemController.class)
class ExpenseBackendApplicationTest {

    @Autowired private SystemController systemController;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void contextStartsWithSystemEndpoint() {
        assertThat(systemController.info().service()).isEqualTo("expense-backend");
    }
}
