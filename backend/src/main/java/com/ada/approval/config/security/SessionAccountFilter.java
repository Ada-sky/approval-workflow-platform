package com.ada.approval.config.security;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.authentication.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Objects;

/**
 * Refreshes account eligibility and authorities for authenticated session requests. Revoked grants
 * take effect without a new login; disabled accounts or changed password hashes invalidate the
 * session.
 */
public class SessionAccountFilter extends OncePerRequestFilter {
    private final UserDetailsService users;

    public SessionAccountFilter(UserDetailsService users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            try {
                UserDetails current = users.loadUserByUsername(authentication.getName());
                boolean passwordChanged =
                        authentication.getPrincipal() instanceof UserDetails
                                && !Objects.equals(
                                        ((UserDetails) authentication.getPrincipal()).getPassword(),
                                        current.getPassword());
                if (!current.isEnabled() || !current.isAccountNonLocked() || passwordChanged)
                    throw new DisabledException("Account unavailable");
                UsernamePasswordAuthenticationToken refreshed =
                        new UsernamePasswordAuthenticationToken(
                                current, null, current.getAuthorities());
                refreshed.setDetails(authentication.getDetails());
                SecurityContextHolder.getContext().setAuthentication(refreshed);
            } catch (AuthenticationException ex) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) session.invalidate();
                if (request.getRequestURI().startsWith(request.getContextPath() + "/api/"))
                    com.ada.approval.api.error.ApiErrors.write(
                            request, response, 401, "Session no longer valid");
                else
                    response.sendError(
                            HttpServletResponse.SC_UNAUTHORIZED, "Session no longer valid");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
