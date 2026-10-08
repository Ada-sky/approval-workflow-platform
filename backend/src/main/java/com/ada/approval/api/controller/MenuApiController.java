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
@RequestMapping("/api/menus")
@Validated
@PreAuthorize("hasAuthority('10023')")
public class MenuApiController {
    @Autowired private AdminApiService service;

    @GetMapping
    public PageResponse<PermissionResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.permissions(page, size);
    }

    @GetMapping("/{id}")
    public PermissionResponse get(
            @PathVariable @Positive(message = "ID must be positive") Integer id) {
        return service.getMenu(id);
    }

    @PostMapping
    public ResponseEntity<PermissionResponse> create(@Valid @RequestBody MenuRequest dto) {
        PermissionResponse response = service.createMenu(dto);
        return ResponseEntity.created(URI.create("/api/menus/" + response.getId())).body(response);
    }

    @PutMapping("/{id}")
    public PermissionResponse update(
            @PathVariable @Positive(message = "ID must be positive") Integer id,
            @Valid @RequestBody MenuRequest dto) {
        return service.updateMenu(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive(message = "ID must be positive") Integer id) {
        service.deleteMenu(id);
    }
}
