package com.ada.approval.api.controller;

import com.ada.approval.api.dto.ApiDtos.*;
import com.ada.approval.api.service.LeaveApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import java.net.URI;
import java.util.List;

/**
 * Exposes employee leave requests without granting access through request IDs alone. The service
 * boundary enforces ownership and delegates workflow transitions to Activiti.
 */
@RestController
@RequestMapping("/api/leave-requests")
@Validated
public class LeaveRequestApiController {
    @Autowired private LeaveApiService service;

    @GetMapping
    public PageResponse<LeaveResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @GetMapping("/{id}")
    public LeaveResponse get(@PathVariable @Positive(message = "ID must be positive") Integer id) {
        return service.own(id);
    }

    @PostMapping
    public ResponseEntity<LeaveResponse> create(@Valid @RequestBody LeaveRequest dto) {
        LeaveResponse response = service.create(dto);
        return ResponseEntity.created(URI.create("/api/leave-requests/" + response.getId()))
                .body(response);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(
            @PathVariable @Positive(message = "ID must be positive") Integer id,
            @Valid @RequestBody LeaveRequest dto) {
        service.update(id, dto);
    }

    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable @Positive(message = "ID must be positive") Integer id) {
        service.archive(id);
    }

    @GetMapping("/{id}/approval-history")
    public List<ApprovalResponse> history(
            @PathVariable @Positive(message = "ID must be positive") Integer id) {
        return service.history(id);
    }
}
