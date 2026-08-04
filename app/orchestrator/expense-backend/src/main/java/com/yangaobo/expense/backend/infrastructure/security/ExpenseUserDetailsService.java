package com.yangaobo.expense.backend.infrastructure.security;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ExpenseUserDetailsService implements UserDetailsService {

    private final JdbcClient jdbcClient;

    public ExpenseUserDetailsService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserRow user =
                jdbcClient
                        .sql(
                                """
                                SELECT username, password_hash, display_name, enabled
                                FROM auth_user
                                WHERE username = :username
                                """)
                        .param("username", username)
                        .query(UserRow.class)
                        .optional()
                        .orElseThrow(() -> new UsernameNotFoundException("账号或密码错误"));
        List<SimpleGrantedAuthority> authorities =
                jdbcClient
                        .sql(
                                """
                                SELECT role
                                FROM auth_user_role
                                WHERE username = :username
                                ORDER BY role
                                """)
                        .param("username", username)
                        .query(String.class)
                        .list()
                        .stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
        return new ExpenseUserDetails(
                user.username(),
                user.passwordHash(),
                user.displayName(),
                user.enabled(),
                authorities);
    }

    private record UserRow(
            String username, String passwordHash, String displayName, boolean enabled) {}
}
