package com.ada.approval.api.dto;

import com.ada.approval.entity.*;

import com.ada.approval.api.dto.ApiDtos.*;

import java.time.*;

import java.util.Date;

/**
 * Maps application entities and workflow task keys into public response DTOs. Scalar relationships
 * are enriched explicitly, avoiding entity graphs and accidental credential serialization.
 */
public final class ApiMapper {

    private ApiMapper() {}

    private static LocalDateTime dateTime(Date date) {
        return date == null
                ? null
                : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private static LocalDate date(Date date) {
        return date == null ? null : new java.sql.Date(date.getTime()).toLocalDate();
    }

    public static LeaveResponse leave(HolidayApply h) {

        LeaveResponse d = new LeaveResponse();
        d.setId(h.getId());
        d.setTitle(h.getTitle());
        d.setLeaveTypeId(h.getHolidayType());

        d.setApplicantAccountId(h.getAccountId());

        d.setReason(h.getReason());
        d.setDays(h.getDays());
        d.setStartDate(dateTime(h.getStartTime()));
        d.setEndDate(dateTime(h.getEndTime()));

        String[] statuses = {
            "UNKNOWN", "SUBMITTED", "IN_APPROVAL", "REJECTED", "COMPLETED", "WITHDRAWN"
        };

        int status = h.getStatus() == null ? 0 : h.getStatus();
        d.setStatus(status >= 0 && status < statuses.length ? statuses[status] : "UNKNOWN");

        d.setSubmittedAt(dateTime(h.getSubmitTime()));

        d.setComment(h.getRemark());
        d.setLatestApprovalResult(h.getApprovalStatus());
        return d;
    }

    public static String stage(String key) {

        if ("manager_check".equals(key)) return "DEPARTMENT_MANAGER";

        if ("boss_check".equals(key)) return "GENERAL_MANAGER";

        if ("hr_check".equals(key)) return "HR";

        return "UNKNOWN";
    }

    public static String taskName(String key) {

        if ("manager_check".equals(key)) return "Department Manager approval";

        if ("boss_check".equals(key)) return "General Manager approval";

        if ("hr_check".equals(key)) return "HR approval";

        return "Approval task";
    }

    public static ApprovalResponse approval(HolidayApproval a) {

        ApprovalResponse d = new ApprovalResponse();
        d.setId(a.getId());
        d.setTaskId(a.getTaskId());
        d.setStage(stage(a.getTaskDefKey()));

        Integer result = a.getResult();
        d.setDecision(
                Integer.valueOf(7).equals(result)
                        ? "WITHDRAWN"
                        : result == null || result < 1 || result > 6
                                ? "UNKNOWN"
                                : result % 2 == 1 ? "APPROVED" : "REJECTED");

        d.setApproverAccountId(a.getUserId());
        d.setApproverUsername(a.getUserName());
        d.setApprovedAt(a.getCreateTime());
        d.setComment(a.getRemark());
        return d;
    }

    public static Employee employee(EmployeeRequest d) {

        Employee e = new Employee();
        e.setEmpName(d.getName());
        e.setEmail(d.getEmail());
        e.setMobile(d.getMobile());
        e.setGender(d.getGender());

        e.setDeptId(d.getDepartmentId());
        e.setTitleCategoryId(d.getJobTitleId());

        e.setBirthday(d.getBirthday() == null ? null : java.sql.Date.valueOf(d.getBirthday()));

        e.setOnBoardDate(
                d.getEmploymentDate() == null
                        ? null
                        : java.sql.Date.valueOf(d.getEmploymentDate()));
        e.setLocation(d.getLocation());
        return e;
    }

    public static EmployeeResponse employee(Employee e) {

        EmployeeResponse d = new EmployeeResponse();
        d.setId(e.getId());
        d.setNumber(e.getEmpNum());
        d.setName(e.getEmpName());

        d.setEmail(e.getEmail());
        d.setMobile(e.getMobile());
        d.setGender(e.getGender());
        d.setDepartmentId(e.getDeptId());

        d.setJobTitleId(e.getTitleCategoryId());
        d.setEmployeeStatusId(e.getEmployStatusId());
        d.setBirthday(date(e.getBirthday()));

        d.setEmploymentDate(date(e.getOnBoardDate()));
        d.setLocation(e.getLocation());
        return d;
    }

    public static Dept department(DepartmentRequest d) {

        Dept e = new Dept();
        e.setDeptNum(d.getNumber());
        e.setDeptName(d.getName());
        e.setParentId(d.getParentId() == null ? 0 : d.getParentId());

        e.setLevel(d.getLevel());
        e.setManagerId(d.getManagerEmployeeId());
        return e;
    }

    public static DepartmentResponse department(Dept e) {

        DepartmentResponse d = new DepartmentResponse();
        d.setId(e.getId());
        d.setNumber(e.getDeptNum());
        d.setName(e.getDeptName());

        d.setParentId(e.getParentId());
        d.setLevel(e.getLevel());
        d.setManagerEmployeeId(e.getManagerId());
        return d;
    }

    public static ReferenceResponse reference(Integer id, String name) {
        ReferenceResponse d = new ReferenceResponse();
        d.setId(id);
        d.setName(name);
        return d;
    }

    public static TitleCategory title(JobTitleRequest d) {

        TitleCategory e = new TitleCategory();
        e.setTitleNum(d.getNumber());
        e.setTitleName(d.getName());
        e.setParentId(d.getParentId() == null ? 0 : d.getParentId());
        e.setLevel(d.getLevel());
        return e;
    }

    public static JobTitleResponse title(TitleCategory e) {

        JobTitleResponse d = new JobTitleResponse();
        d.setId(e.getId());
        d.setNumber(e.getTitleNum());
        d.setName(displayJobTitle(e.getTitleName()));
        d.setDisplayName(displayJobTitle(e.getTitleName()));
        d.setParentId(e.getParentId());
        d.setLevel(e.getLevel());
        return d;
    }

    /** Return English aliases without modifying persisted workflow titles. */
    public static String displayJobTitle(String name) {

        return com.ada.approval.workflow.WorkflowTitleNames.display(name);
    }

    public static Menu menu(MenuRequest d) {

        Menu e = new Menu();
        e.setMenuName(d.getName());
        e.setOptValue(d.getPermissionCode());
        e.setUrl(d.getUrl());
        e.setParentId(d.getParentId() == null ? 0 : d.getParentId());
        e.setGrade(d.getGrade());
        return e;
    }

    public static PermissionResponse permission(Menu e) {

        PermissionResponse d = new PermissionResponse();
        d.setId(e.getId());
        d.setName(e.getMenuName());
        d.setCode(e.getOptValue());
        d.setUrl(e.getUrl());
        d.setParentId(e.getParentId());
        d.setGrade(e.getGrade());
        return d;
    }

    public static AssignmentResponse assignment(AccountRole e) {
        AssignmentResponse d = new AssignmentResponse();
        d.setId(e.getId());
        d.setRoleId(e.getRoleId());
        d.setAccountId(e.getAccountId());
        return d;
    }
}
