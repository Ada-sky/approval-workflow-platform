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
@RequestMapping("/api/departments")
@Validated
@PreAuthorize("hasAuthority('10011')")
public class DepartmentApiController {
    @Autowired private AdminApiService service;

    @GetMapping("/manager-options")
    public PageResponse<ReferenceResponse> managerOptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String name) {
        return service.managerOptions(page, size, name);
    }

    @GetMapping
    public PageResponse<DepartmentResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name) {
        return service.departments(page, size, name);
    }

    @GetMapping("/{id}")
    public DepartmentResponse get(
            @PathVariable @Positive(message = "ID must be positive") Integer id) {
        return service.getDepartment(id);
    }

    @PostMapping
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest dto) {
        DepartmentResponse response = service.createDepartment(dto);
        return ResponseEntity.created(URI.create("/api/departments/" + response.getId()))
                .body(response);
    }

    @PutMapping("/{id}")
    public DepartmentResponse update(
            @PathVariable @Positive(message = "ID must be positive") Integer id,
            @Valid @RequestBody DepartmentRequest dto) {
        return service.updateDepartment(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive(message = "ID must be positive") Integer id) {
        service.deleteDepartment(id);
    }
}
