package com.yangaobo.expense.backend.infrastructure.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

public final class ExpenseUserDetails extends User {

    private final String displayName;

    public ExpenseUserDetails(
            String username,
            String password,
            String displayName,
            boolean enabled,
            Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, true, authorities);
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
