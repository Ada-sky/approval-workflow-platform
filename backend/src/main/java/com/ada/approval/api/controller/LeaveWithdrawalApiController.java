package com.ada.approval.api.controller;

import com.ada.approval.api.service.LeaveWithdrawalService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Exposes applicant withdrawal while leaving eligibility and process termination to the
 * transactional service.
 */
@RestController
@Validated
public class LeaveWithdrawalApiController {
    private final LeaveWithdrawalService withdrawals;

    public LeaveWithdrawalApiController(LeaveWithdrawalService withdrawals) {
        this.withdrawals = withdrawals;
    }

    @PostMapping("/api/leave/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable @Positive(message = "ID must be positive") Integer id) {
        withdrawals.withdraw(id);
    }
}
