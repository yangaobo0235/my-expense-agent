package com.yangaobo.expense.backend.infrastructure.security;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yangaobo.expense.backend.interfaces.rest.AuthController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfiguration.class, SecurityErrorWriter.class})
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    void anonymousSessionShouldIssueCsrfCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void validCredentialsShouldCreateAuthenticatedSession() throws Exception {
        when(userDetailsService.loadUserByUsername("finance01"))
                .thenReturn(
                        new ExpenseUserDetails(
                                "finance01",
                                new BCryptPasswordEncoder(12).encode("correct-password"),
                                "财务管理员",
                                true,
                                List.of(new SimpleGrantedAuthority("ROLE_FINANCE_ADMIN"))));

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"username":"finance01","password":"correct-password"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.displayName").value("财务管理员"))
                .andExpect(jsonPath("$.roles[0]").value("FINANCE_ADMIN"));
    }

    @Test
    void invalidCredentialsShouldReturnUnauthorized() throws Exception {
        when(userDetailsService.loadUserByUsername("finance01"))
                .thenReturn(
                        new ExpenseUserDetails(
                                "finance01",
                                new BCryptPasswordEncoder(12).encode("correct-password"),
                                "财务管理员",
                                true,
                                List.of(new SimpleGrantedAuthority("ROLE_FINANCE_ADMIN"))));

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"username":"finance01","password":"wrong-password"}
                                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }
}
