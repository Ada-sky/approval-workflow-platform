package com.ada.approval.enums;

public enum HolidayApprovalStatus {
    MANAGER_AGREE("Approved by Department Manager", 1),
    MANAGER_DISAGREE("Rejected by Department Manager", 2),
    BOSS_AGREE("Approved by General Manager", 3),
    BOSS_DISAGREE("Rejected by General Manager", 4),
    HR_AGREE("Approved by HR", 5),
    HR_DISAGREE("Rejected by HR", 6),
    WITHDRAWN("Withdrawn by applicant", 7),
    ;

    private Integer type; // Numeric workflow status code
    private String desc; // Display description for the numeric code

    HolidayApprovalStatus(String desc, Integer type) {
        this.type = type;
        this.desc = desc;
    }

    public Integer getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }
}
