package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.infrastructure.security.ExpenseUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final CookieCsrfTokenRepository csrfTokenRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public AuthController(
            AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/session")
    public SessionResponse session(
            Authentication authentication,
            CsrfToken csrfToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        csrfTokenRepository.saveToken(csrfToken, request, response);
        return SessionResponse.from(authentication);
    }

    @PostMapping("/login")
    public SessionResponse login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response,
            CsrfToken csrfToken) {
        Authentication authentication;
        try {
            authentication =
                    authenticationManager.authenticate(
                            UsernamePasswordAuthenticationToken.unauthenticated(
                                    body.username().trim(), body.password()));
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        csrfTokenRepository.saveToken(csrfToken, request, response);
        return SessionResponse.from(authentication);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = securityContextHolderStrategy.getContext().getAuthentication();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        csrfTokenRepository.saveToken(null, request, response);
        securityContextRepository.saveContext(
                securityContextHolderStrategy.createEmptyContext(), request, response);
        securityContextHolderStrategy.clearContext();
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record SessionResponse(
            boolean authenticated, String subject, String displayName, List<String> roles) {

        static SessionResponse from(Authentication authentication) {
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken) {
                return new SessionResponse(false, null, null, List.of());
            }
            String displayName =
                    authentication.getPrincipal() instanceof ExpenseUserDetails user
                            ? user.displayName()
                            : authentication.getName();
            List<String> roles =
                    authentication.getAuthorities().stream()
                            .map(authority -> authority.getAuthority())
                            .filter(authority -> authority.startsWith("ROLE_"))
                            .map(authority -> authority.substring(5))
                            .sorted()
                            .toList();
            return new SessionResponse(true, authentication.getName(), displayName, roles);
        }
    }
}
