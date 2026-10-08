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
@RequestMapping("/api/employee-statuses")
@Validated
@PreAuthorize("hasAuthority('10013')")
public class EmployeeStatusApiController {
    @Autowired private AdminApiService service;

    @GetMapping
    public PageResponse<ReferenceResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name) {
        return service.statuses(page, size, name);
    }

    @GetMapping("/{id}")
    public ReferenceResponse get(
            @PathVariable @Positive(message = "ID must be positive") Integer id) {
        return service.getStatus(id);
    }

    @PostMapping
    public ResponseEntity<ReferenceResponse> create(@Valid @RequestBody NameRequest dto) {
        ReferenceResponse response = service.createStatus(dto);
        return ResponseEntity.created(URI.create("/api/employee-statuses/" + response.getId()))
                .body(response);
    }

    @PutMapping("/{id}")
    public ReferenceResponse update(
            @PathVariable @Positive(message = "ID must be positive") Integer id,
            @Valid @RequestBody NameRequest dto) {
        return service.updateStatus(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive(message = "ID must be positive") Integer id) {
        service.deleteStatus(id);
    }
}
