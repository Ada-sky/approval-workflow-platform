package com.ada.approval.config.security;

import com.ada.approval.repository.PermissionRepository;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

/**
 * Resolves approval-function access from existing menu grants. Workbench URLs remain permission
 * identities after retiring the MVC UI; this check does not authorize an individual task.
 */
@Component("backendAuthorization")
public class BackendAuthorization {
    private final PermissionRepository permissions;

    public BackendAuthorization(PermissionRepository permissions) {
        this.permissions = permissions;
    }

    public boolean canApprove(Authentication authentication) {
        return canAccessMenu(authentication, "/workBench");
    }

    public boolean canAccessMenu(Authentication authentication, String root) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) return false;
        return permissions.findGrantedMenuUrls(authentication.getName()).stream()
                .anyMatch(
                        url -> {
                            if (url == null) return false;
                            String path = url.trim().split("[?#]", 2)[0];
                            if (!path.startsWith("/")) path = "/" + path;
                            return path.equals(root) || path.startsWith(root + "/");
                        });
    }
}
