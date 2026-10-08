package com.ada.approval.api.controller;

import com.ada.approval.api.dto.ApiDtos.*;
import com.ada.approval.api.service.AdminApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import java.net.URI;

@RestController
@RequestMapping("/api/roles")
@Validated
@PreAuthorize("hasAuthority('10022')")
public class RoleApiController {
    @Autowired private AdminApiService service;

    @GetMapping
    public PageResponse<ReferenceResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name) {
        return service.roles(page, size, name);
    }

    @GetMapping("/{id}")
    public ReferenceResponse get(
            @PathVariable @Positive(message = "ID must be positive") Integer id) {
        return service.getRole(id);
    }

    @PostMapping
    public ResponseEntity<ReferenceResponse> create(@Valid @RequestBody NameRequest dto) {
        ReferenceResponse response = service.createRole(dto);
        return ResponseEntity.created(URI.create("/api/roles/" + response.getId())).body(response);
    }

    @PutMapping("/{id}")
    public ReferenceResponse update(
            @PathVariable @Positive(message = "ID must be positive") Integer id,
            @Valid @RequestBody NameRequest dto) {
        return service.updateRole(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive(message = "ID must be positive") Integer id) {
        service.deleteRole(id);
    }

    @GetMapping("/{id}/assignments")
    public PageResponse<AssignmentResponse> assignments(
            @PathVariable @Positive(message = "ID must be positive") Integer id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.assignments(id, page, size);
    }

    @PostMapping("/{id}/employees/{employeeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(
            @PathVariable @Positive(message = "ID must be positive") Integer id,
            @PathVariable @Positive(message = "Employee ID must be positive") Integer employeeId) {
        service.assign(id, employeeId);
    }

    @DeleteMapping("/{id}/accounts/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(
            @PathVariable @Positive(message = "ID must be positive") Integer id,
            @PathVariable @Positive(message = "Account ID must be positive") Integer accountId) {
        service.unassign(id, accountId);
    }

    @GetMapping("/{id}/permissions")
    public java.util.List<PermissionResponse> grants(
            @PathVariable @Positive(message = "ID must be positive") Integer id) {
        return service.grants(id);
    }

    @PutMapping("/{id}/permissions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grants(
            @PathVariable @Positive(message = "ID must be positive") Integer id,
            @Valid @RequestBody GrantRequest dto) {
        service.grants(id, dto);
    }
}
