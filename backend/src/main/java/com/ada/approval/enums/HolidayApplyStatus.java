package com.ada.approval.enums;

public enum HolidayApplyStatus {
    SUBMIT("Submitted", 1),
    APPROVAL("In review", 2),
    REJECTED("Rejected", 3),
    COMPLETED("Completed", 4),
    WITHDRAWN("Withdrawn", 5);
    private Integer type; // Numeric workflow status code
    private String desc; // Display description for the numeric code

    HolidayApplyStatus(String desc, Integer type) {
        this.desc = desc;
        this.type = type;
    }

    public Integer getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }
}
