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
@RequestMapping("/api/job-titles")
@Validated
@PreAuthorize("hasAuthority('10012')")
public class JobTitleApiController {
    @Autowired private AdminApiService service;

    @GetMapping
    public PageResponse<JobTitleResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name) {
        return service.titles(page, size, name);
    }

    @GetMapping("/{id}")
    public JobTitleResponse get(
            @PathVariable @Positive(message = "ID must be positive") Integer id) {
        return service.getTitle(id);
    }

    @PostMapping
    public ResponseEntity<JobTitleResponse> create(@Valid @RequestBody JobTitleRequest dto) {
        JobTitleResponse response = service.createTitle(dto);
        return ResponseEntity.created(URI.create("/api/job-titles/" + response.getId()))
                .body(response);
    }

    @PutMapping("/{id}")
    public JobTitleResponse update(
            @PathVariable @Positive(message = "ID must be positive") Integer id,
            @Valid @RequestBody JobTitleRequest dto) {
        return service.updateTitle(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive(message = "ID must be positive") Integer id) {
        service.deleteTitle(id);
    }
}
