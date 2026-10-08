package com.ada.approval.config.security;

import com.ada.approval.entity.Account;
import com.ada.approval.service.IAccountService;
import com.ada.approval.service.IPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.http.HttpMethod;

import java.util.stream.Collectors;

/**
 * Configures session/form authentication, CSRF protection and numeric permission-based endpoint
 * access. Unmatched routes are denied. Approval access also requires service-level task
 * authorization; no role-name shortcut applies.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    @Autowired private HpAuthenticationSuccessHandler successHandler;
    @Autowired private HpAuthenticationFailureHandler failureHandler;
    @Autowired private IAccountService accountService;
    @Autowired private IPermissionService permissionService;
    @Autowired private BackendAuthorization authorization;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // React uses session authentication and CSRF; no server-rendered login page remains.
        http.headers(headers -> headers.frameOptions(frame -> frame.deny()))
                .formLogin(
                        form ->
                                form.usernameParameter("userName")
                                        .passwordParameter("password")
                                        .loginPage("/login")
                                        .loginProcessingUrl("/login")
                                        .successHandler(successHandler)
                                        .failureHandler(failureHandler))
                .logout(logout -> logout.disable())
                .rememberMe(remember -> remember.disable())
                // Keep automatic session-context persistence used by the existing refresh filter.
                .securityContext(security -> security.requireExplicitSave(false))
                .authenticationProvider(authenticationProvider())
                .authorizeHttpRequests(
                        requests ->
                                requests.requestMatchers(
                                                request ->
                                                        request.getDispatcherType()
                                                                == jakarta.servlet.DispatcherType
                                                                        .ERROR)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/login")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf")
                                        .permitAll()
                                        .requestMatchers(
                                                "/api/auth/me",
                                                "/api/auth/logout",
                                                "/api/auth/password")
                                        .fullyAuthenticated()
                                        .requestMatchers(HttpMethod.POST, "/api/employees")
                                        .hasAuthority("100211")
                                        .requestMatchers(HttpMethod.PUT, "/api/employees/*")
                                        .hasAuthority("100212")
                                        .requestMatchers(HttpMethod.DELETE, "/api/employees/*")
                                        .hasAuthority("100213")
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/employees/form-options")
                                        .hasAnyAuthority("100211", "100212")
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/employees",
                                                "/api/employees/*")
                                        .hasAnyAuthority("10021", "100214", "10022")
                                        .requestMatchers("/api/departments/**")
                                        .hasAuthority("10011")
                                        .requestMatchers("/api/roles/**")
                                        .hasAuthority("10022")
                                        .requestMatchers(
                                                "/api/admin/leave-types",
                                                "/api/admin/leave-types/**")
                                        .hasAuthority("10023")
                                        .requestMatchers("/api/menus/**")
                                        .hasAuthority("10023")
                                        .requestMatchers("/api/permissions")
                                        .hasAnyAuthority("10022", "10023")
                                        .requestMatchers("/api/employee-statuses/**")
                                        .hasAuthority("10013")
                                        .requestMatchers("/api/job-titles/**")
                                        .hasAuthority("10012")
                                        .requestMatchers("/api/approvals/**")
                                        .access(
                                                (auth, context) ->
                                                        new AuthorizationDecision(
                                                                authorization.canApprove(
                                                                        auth.get())))
                                        .requestMatchers(
                                                "/api/leave-requests/**", "/api/leave-types")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.POST, "/api/leave/*/withdraw")
                                        .authenticated()
                                        .requestMatchers(
                                                "/swagger-ui.html",
                                                "/swagger-ui/**",
                                                "/v3/api-docs/**",
                                                "/webjars/**")
                                        .hasAuthority("10022")
                                        .anyRequest()
                                        .denyAll());
        http.exceptionHandling(
                errors ->
                        errors.authenticationEntryPoint(
                                        (request, response, exception) -> {
                                            com.ada.approval.api.error.ApiErrors.write(
                                                    request,
                                                    response,
                                                    401,
                                                    "Authentication required");
                                        })
                                .accessDeniedHandler(
                                        (request, response, exception) -> {
                                            if (request.getRequestURI()
                                                    .startsWith(request.getContextPath() + "/api/"))
                                                com.ada.approval.api.error.ApiErrors.write(
                                                        request,
                                                        response,
                                                        403,
                                                        "Access denied or invalid CSRF token");
                                            else
                                                new org.springframework.security.web.access
                                                                .AccessDeniedHandlerImpl()
                                                        .handle(request, response, exception);
                                        }));
        // Construct here rather than as a servlet-filter bean to avoid duplicate registration.
        http.addFilterBefore(
                new SessionAccountFilter(userDetailsService()), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            Account account = accountService.findAccountByUserName(username);
            if (account == null || !account.isEnabled())
                throw new UsernameNotFoundException("Account unavailable");
            account.setGrantedAuthority(
                    permissionService.findAuthorityByUserName(username).stream()
                            .filter(value -> value != null && !value.trim().isEmpty())
                            .map(SimpleGrantedAuthority::new)
                            .distinct()
                            .collect(Collectors.toList()));
            return account;
        };
    }

    @Bean
    public static PasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService());
        provider.setPasswordEncoder(encoder());
        return provider;
    }
}
