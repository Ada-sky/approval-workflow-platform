package com.ada.approval.api.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.*;
import java.util.*;

/**
 * Defines explicit REST payloads rather than serializing persistence entities or Activiti objects.
 * Workflow progress is a backend projection, distinct from recorded approval decisions.
 */
public final class ApiDtos {
    private ApiDtos() {}

    @Data
    public static class LeaveRequest {
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        private String title;

        @NotNull(message = "Leave type is required")
        @Positive(message = "Leave type ID must be positive")
        private Integer leaveTypeId;

        @NotBlank(message = "Reason is required")
        @Size(max = 1000, message = "Reason must be at most 1000 characters")
        private String reason;

        @NotNull(message = "Start date is required")
        private LocalDateTime startDate;

        @NotNull(message = "End date is required")
        private LocalDateTime endDate;

        @Size(max = 1000, message = "Comment must be at most 1000 characters")
        private String comment;

        @AssertTrue(message = "End date must be on or after start date")
        @JsonIgnore
        public boolean isDateRangeValid() {
            return startDate == null || endDate == null || !endDate.isBefore(startDate);
        }
    }

    @Data
    public static class LeaveResponse {
        private Integer id;
        private String title;
        private Integer leaveTypeId;
        private String reason;
        private Integer days;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String applicantName;

        @com.fasterxml.jackson.annotation.JsonFormat(
                shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        private LocalDateTime submittedAt;

        private String status;
        private String comment;
        private Integer latestApprovalResult;

        @com.fasterxml.jackson.annotation.JsonInclude(
                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private WorkflowProgress workflowProgress;

        private Integer applicantAccountId;
        private boolean canWithdraw;
    }

    @Data
    public static class WorkflowProgress {
        private String currentStage;
        private String message;
        private List<WorkflowProgressStage> stages;
    }

    @Data
    public static class WorkflowProgressStage {
        private String stage;
        private String label;
        private String status;

        public WorkflowProgressStage(String stage, String label, String status) {
            this.stage = stage;
            this.label = label;
            this.status = status;
        }
    }

    @Data
    public static class ApprovalDecision {
        @NotNull(message = "Leave request ID is required")
        @Positive(message = "Leave request ID must be positive")
        private Integer leaveRequestId;

        @Size(max = 1000, message = "Comment must be at most 1000 characters")
        private String comment;
    }

    @Data
    public static class TaskResponse {
        private String taskId;
        private String name;
        private String stage;
        private LeaveResponse leaveRequest;
        private List<ApprovalResponse> approvalHistory;
    }

    @Data
    public static class ApprovalResponse {
        private Integer id;
        private String taskId;
        private String stage;
        private String decision;
        private Integer approverAccountId;
        private String approverUsername;
        private LocalDateTime approvedAt;
        private String comment;
    }

    @Data
    public static class ApproverHistoryResponse {
        private Integer requestId;
        private String title;
        private String applicantName;
        private String leaveType;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String decision;
        private String stage;
        private LocalDateTime decidedAt;
    }

    @Data
    public static class EmployeeRequest {
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        private String name;

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must be at most 254 characters")
        private String email;

        @NotBlank(message = "Mobile number is required")
        @Size(max = 30, message = "Mobile number must be at most 30 characters")
        private String mobile;

        @Size(max = 20, message = "Gender must be at most 20 characters")
        private String gender;

        @Positive(message = "Department ID must be positive")
        private Integer departmentId;

        @Positive(message = "Job title ID must be positive")
        private Integer jobTitleId;

        @PastOrPresent(message = "Birthday cannot be in the future")
        private LocalDate birthday;

        private LocalDate employmentDate;

        @Size(max = 200, message = "Location must be at most 200 characters")
        private String location;
    }

    @Data
    @lombok.EqualsAndHashCode(callSuper = true)
    public static class CreateEmployeeRequest extends EmployeeRequest {}

    @Data
    @lombok.EqualsAndHashCode(callSuper = true)
    @lombok.ToString(exclude = "temporaryPassword")
    public static class EmployeeCreatedResponse extends EmployeeResponse {
        private String temporaryPassword;
    }

    @Data
    public static class EmployeeResponse {
        private Integer id;
        private String number;
        private String name;
        private String email;
        private String mobile;
        private String gender;
        private Integer departmentId;
        private Integer jobTitleId;
        private Integer employeeStatusId;
        private LocalDate birthday;
        private LocalDate employmentDate;
        private String location;
        private String departmentName;
        private String jobTitleName;
        private String employeeStatusName;
    }

    @Data
    public static class EmployeeFormOptions {
        private List<ReferenceResponse> departments;
        private List<ReferenceResponse> jobTitles;
    }

    @Data
    public static class DepartmentRequest {
        @NotBlank(message = "Department number is required")
        @Size(max = 50, message = "Department number must be at most 50 characters")
        private String number;

        @NotBlank(message = "Department name is required")
        @Size(max = 100, message = "Department name must be at most 100 characters")
        private String name;

        @Min(value = 0, message = "Parent ID cannot be negative")
        private Integer parentId;

        @NotNull(message = "Level is required")
        @Min(value = 0, message = "Level cannot be negative")
        private Integer level;

        @Positive(message = "Manager employee ID must be positive")
        private Integer managerEmployeeId;
    }

    @Data
    public static class DepartmentResponse {
        private Integer id;
        private String number;
        private String name;
        private Integer parentId;
        private Integer level;
        private Integer managerEmployeeId;
        private String managerName;
        private String parentDepartmentName;
    }

    @Data
    public static class NameRequest {
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        private String name;
    }

    @Data
    public static class LeaveTypeRequest {
        @NotBlank(message = "Leave type name is required")
        @Size(max = 255, message = "Leave type name must be at most 255 characters")
        private String name;

        public void setName(String name) {
            this.name = name == null ? null : name.strip();
        }
    }

    @Data
    public static class ReferenceResponse {
        private Integer id;
        private String name;
    }

    @Data
    public static class JobTitleRequest {
        @NotBlank(message = "Job title number is required")
        @Size(max = 50, message = "Job title number must be at most 50 characters")
        private String number;

        @NotBlank(message = "Job title name is required")
        @Size(max = 100, message = "Job title name must be at most 100 characters")
        private String name;

        @Min(value = 0, message = "Parent ID cannot be negative")
        private Integer parentId;

        @NotNull(message = "Level is required")
        @Min(value = 0, message = "Level cannot be negative")
        private Integer level;
    }

    @Data
    public static class JobTitleResponse {
        private String displayName;
        private Integer id;
        private String number;
        private String name;
        private Integer parentId;
        private Integer level;
    }

    @Data
    public static class MenuRequest {
        @NotBlank(message = "Menu name is required")
        @Size(max = 100, message = "Menu name must be at most 100 characters")
        private String name;

        @NotBlank(message = "Permission code is required")
        @Size(max = 100, message = "Permission code must be at most 100 characters")
        private String permissionCode;

        @Size(max = 500, message = "URL must be at most 500 characters")
        private String url;

        @Min(value = 0, message = "Parent ID cannot be negative")
        private Integer parentId;

        @NotNull(message = "Grade is required")
        @Min(value = 0, message = "Grade must be 0 to 2")
        @Max(value = 2, message = "Grade must be 0 to 2")
        private Integer grade;
    }

    @Data
    public static class PermissionResponse {
        private Integer id;
        private String name;
        private String code;
        private String url;
        private Integer parentId;
        private Integer grade;
    }

    @Data
    public static class GrantRequest {
        @NotNull(message = "Menu IDs are required")
        @Size(max = 500, message = "At most 500 grants are allowed")
        private List<
                        @NotNull(message = "Menu ID is required")
                        @Positive(message = "Menu ID must be positive") Integer>
                menuIds;
    }

    @Data
    public static class AssignmentResponse {
        private String accountDisplayName;
        private String accountEmail;
        private Integer id;
        private Integer accountId;
        private Integer roleId;
    }

    @Data
    public static class CurrentUserResponse {
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private String displayName;

        private Integer accountId;
        private Integer employeeId;
        private String username;
        private List<String> authorities;
        private boolean canAccessApprovals;
        private boolean canApplyForLeave;
    }

    @Data
    public static class CsrfResponse {
        private String token;
        private String headerName;
        private String parameterName;
    }

    @Data
    public static class PasswordRequest {
        @NotBlank(message = "Current password is required")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private String currentPassword;

        @NotBlank(message = "New password is required")
        @Size(min = 12, max = 72, message = "New password must contain 12 to 72 characters")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private String newPassword;

        @NotBlank(message = "Password confirmation is required")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private String confirmation;

        @AssertTrue(message = "Password confirmation must match")
        @JsonIgnore
        public boolean isConfirmationValid() {
            return newPassword == null || confirmation == null || newPassword.equals(confirmation);
        }

        @AssertTrue(message = "New password must be at most 72 UTF-8 bytes")
        @JsonIgnore
        public boolean isPasswordByteLengthValid() {
            return newPassword == null
                    || newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
        }
    }

    @Data
    public static class PageResponse<T> {
        private List<T> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;

        public PageResponse(List<T> content, int page, int size, long total) {
            this.content = content;
            this.page = page;
            this.size = size;
            this.totalElements = total;
            this.totalPages = (int) Math.ceil((double) total / size);
        }
    }
}
