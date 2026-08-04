package com.yangaobo.expense.backend.infrastructure.security;

import java.util.Map;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);
    private static final List<String> ADMIN_ROLES =
            List.of("STUDENT", "ADVISOR", "COLLEGE_REVIEWER", "FINANCE_ADMIN", "AUDITOR");

    private final JdbcClient jdbcClient;
    private final PasswordEncoder passwordEncoder;
    private final ExpenseAuthProperties properties;

    public BootstrapAdminInitializer(
            JdbcClient jdbcClient,
            PasswordEncoder passwordEncoder,
            ExpenseAuthProperties properties) {
        this.jdbcClient = jdbcClient;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.bootstrapEnabled()) {
            log.warn("未配置 EXPENSE_BOOTSTRAP_ADMIN_PASSWORD，未创建初始管理员账号");
            return;
        }
        boolean exists =
                jdbcClient
                        .sql("SELECT EXISTS(SELECT 1 FROM auth_user WHERE username = :username)")
                        .param("username", properties.bootstrapUsername())
                        .query(Boolean.class)
                        .single();
        if (exists) {
            jdbcClient
                    .sql(
                            "UPDATE auth_user SET display_name = :displayName "
                                    + "WHERE username = :username")
                    .params(
                            Map.of(
                                    "username", properties.bootstrapUsername(),
                                    "displayName", properties.bootstrapDisplayName()))
                    .update();
            ensureAdminRoles();
            return;
        }
        jdbcClient
                .sql(
                        """
                        INSERT INTO auth_user (username, password_hash, display_name)
                        VALUES (:username, :passwordHash, :displayName)
                        """)
                .params(
                        Map.of(
                                "username", properties.bootstrapUsername(),
                                "passwordHash", passwordEncoder.encode(properties.bootstrapPassword()),
                                "displayName", properties.bootstrapDisplayName()))
                .update();
        ensureAdminRoles();
        log.info("已创建初始管理员账号：{}", properties.bootstrapUsername());
    }

    private void ensureAdminRoles() {
        for (String role : ADMIN_ROLES) {
            jdbcClient
                    .sql(
                            """
                            INSERT INTO auth_user_role (username, role)
                            VALUES (:username, :role)
                            ON CONFLICT (username, role) DO NOTHING
                            """)
                    .params(Map.of("username", properties.bootstrapUsername(), "role", role))
                    .update();
        }
    }
}
