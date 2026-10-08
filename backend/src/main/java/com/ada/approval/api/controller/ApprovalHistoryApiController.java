package com.ada.approval.api.controller;

import com.ada.approval.api.dto.ApiDtos.*;
import com.ada.approval.api.service.LeaveApiService;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Read-only access to the authenticated approver's recorded decisions. */
@RestController
@RequestMapping("/api/approvals/history")
@Validated
@PreAuthorize("@backendAuthorization.canApprove(authentication)")
public class ApprovalHistoryApiController {
    @Autowired private LeaveApiService service;

    @GetMapping
    public PageResponse<ApproverHistoryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.approverHistory(page, size);
    }

    @GetMapping("/{id}")
    public TaskResponse details(
            @PathVariable @Positive(message = "ID must be positive") Integer id) {
        return service.historicalDetails(id);
    }
}
