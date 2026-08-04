package com.yangaobo.expense.backend.infrastructure.security;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
        prefix = "expense.auth",
        name = "demo-users-enabled",
        havingValue = "true")
public class DemoUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserInitializer.class);
    private static final List<DemoUser> USERS =
            List.of(
                    new DemoUser("student", "学生", "STUDENT"),
                    new DemoUser("advisor", "指导教师", "ADVISOR"),
                    new DemoUser("college_reviewer", "学院审核员", "COLLEGE_REVIEWER"),
                    new DemoUser("finance_admin", "财务管理员", "FINANCE_ADMIN"),
                    new DemoUser("auditor", "审计员", "AUDITOR"));

    private final JdbcClient jdbcClient;
    private final PasswordEncoder passwordEncoder;

    public DemoUserInitializer(JdbcClient jdbcClient, PasswordEncoder passwordEncoder) {
        this.jdbcClient = jdbcClient;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.warn("本地演示账号初始化已启用；禁止在生产环境使用");
        USERS.forEach(this::ensureUser);
    }

    private void ensureUser(DemoUser user) {
        boolean exists =
                jdbcClient
                        .sql("SELECT EXISTS(SELECT 1 FROM auth_user WHERE username = :username)")
                        .param("username", user.username())
                        .query(Boolean.class)
                        .single();
        if (!exists) {
            jdbcClient
                    .sql(
                            """
                            INSERT INTO auth_user (username, password_hash, display_name)
                            VALUES (:username, :passwordHash, :displayName)
                            """)
                    .params(
                            Map.of(
                                    "username", user.username(),
                                    "passwordHash", passwordEncoder.encode(user.username()),
                                    "displayName", user.displayName()))
                    .update();
        }
        jdbcClient
                .sql(
                        """
                        INSERT INTO auth_user_role (username, role)
                        VALUES (:username, :role)
                        ON CONFLICT (username, role) DO NOTHING
                        """)
                .params(Map.of("username", user.username(), "role", user.role()))
                .update();
    }

    private record DemoUser(String username, String displayName, String role) {}
}
