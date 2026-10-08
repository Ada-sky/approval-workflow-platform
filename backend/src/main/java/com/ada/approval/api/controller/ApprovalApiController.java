package com.ada.approval.api.controller;

import com.ada.approval.api.dto.ApiDtos.*;
import com.ada.approval.api.service.LeaveApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Exposes approval tasks and decisions through the REST API. Workbench permission grants allow
 * access to this functionality; services also authorize each specific task.
 */
@RestController
@RequestMapping("/api/approvals/tasks")
@Validated
@PreAuthorize("@backendAuthorization.canApprove(authentication)")
public class ApprovalApiController {
    @Autowired private LeaveApiService service;

    @GetMapping
    public PageResponse<TaskResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.taskList(page, size);
    }

    @GetMapping("/{taskId}")
    public TaskResponse get(
            @PathVariable
                    @Size(min = 1, max = 128, message = "Task ID must contain 1 to 128 characters")
                    String taskId) {
        return service.task(taskId);
    }

    @PostMapping("/{taskId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(
            @PathVariable
                    @Size(min = 1, max = 128, message = "Task ID must contain 1 to 128 characters")
                    String taskId,
            @Valid @RequestBody ApprovalDecision dto) {
        service.decide(taskId, dto, true);
    }

    @PostMapping("/{taskId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(
            @PathVariable
                    @Size(min = 1, max = 128, message = "Task ID must contain 1 to 128 characters")
                    String taskId,
            @Valid @RequestBody ApprovalDecision dto) {
        service.decide(taskId, dto, false);
    }
}
