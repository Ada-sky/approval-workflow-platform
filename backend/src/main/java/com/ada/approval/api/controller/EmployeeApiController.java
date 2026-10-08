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

/**
 * Exposes permission-protected employee administration. Creation returns a temporary password once;
 * ordinary profile edits preserve employee status.
 */
@RestController
@RequestMapping("/api/employees")
@Validated
public class EmployeeApiController {
    @Autowired private AdminApiService service;

    @GetMapping("/form-options")
    @PreAuthorize("hasAnyAuthority('100211','100212')")
    public EmployeeFormOptions formOptions() {
        return service.employeeFormOptions();
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('10021','100214','10022')")
    public PageResponse<EmployeeResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name) {
        return service.employees(page, size, name);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('10021','100214','10022')")
    public EmployeeResponse get(
            @PathVariable @Positive(message = "ID must be positive") Integer id) {
        return service.getEmployee(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('100211')")
    public ResponseEntity<EmployeeCreatedResponse> create(
            @Valid @RequestBody CreateEmployeeRequest dto) {
        EmployeeCreatedResponse r = service.createEmployee(dto);
        return ResponseEntity.created(URI.create("/api/employees/" + r.getId()))
                .cacheControl(CacheControl.noStore())
                .body(r);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('100212')")
    public EmployeeResponse update(
            @PathVariable @Positive(message = "ID must be positive") Integer id,
            @Valid @RequestBody EmployeeRequest dto) {
        return service.updateEmployee(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('100213')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive(message = "ID must be positive") Integer id) {
        service.deleteEmployee(id);
    }
}
