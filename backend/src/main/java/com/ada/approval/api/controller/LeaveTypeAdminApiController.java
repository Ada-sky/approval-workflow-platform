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
 * Exposes permission-protected leave-type mutations. Reference checks prevent deletion of a type
 * used by an existing request.
 */
@RestController
@RequestMapping("/api/admin/leave-types")
@Validated
@PreAuthorize("hasAuthority('10023')")
public class LeaveTypeAdminApiController {
    @Autowired private AdminApiService service;

    @GetMapping
    public PageResponse<ReferenceResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name) {
        return service.searchLeaveTypes(page, size, name);
    }

    @GetMapping("/{id}")
    public ReferenceResponse get(@PathVariable @Positive Integer id) {
        return service.getLeaveType(id);
    }

    @PostMapping
    public ResponseEntity<ReferenceResponse> create(@Valid @RequestBody LeaveTypeRequest dto) {
        ReferenceResponse response = service.createLeaveType(dto);
        return ResponseEntity.created(URI.create("/api/admin/leave-types/" + response.getId()))
                .body(response);
    }

    @PutMapping("/{id}")
    public ReferenceResponse update(
            @PathVariable @Positive Integer id, @Valid @RequestBody LeaveTypeRequest dto) {
        return service.updateLeaveType(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive Integer id) {
        service.deleteLeaveType(id);
    }
}
