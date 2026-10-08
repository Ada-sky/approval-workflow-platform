package com.ada.approval.api.controller;

import com.ada.approval.api.dto.ApiDtos.*;
import com.ada.approval.api.service.AdminApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Provides reference data for authenticated application forms without exposing administration
 * mutations.
 */
@RestController
@RequestMapping("/api")
public class ReferenceApiController {
    @Autowired private AdminApiService service;

    @GetMapping("/leave-types")
    public PageResponse<ReferenceResponse> leaveTypes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.leaveTypes(page, size);
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAnyAuthority('10022','10023')")
    public PageResponse<PermissionResponse> permissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.permissions(page, size);
    }
}
