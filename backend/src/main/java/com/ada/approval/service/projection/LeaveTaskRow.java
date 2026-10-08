package com.ada.approval.service.projection;

import com.ada.approval.entity.HolidayApply;
import lombok.Data;

@Data
public class LeaveTaskRow extends HolidayApply {
    @com.fasterxml.jackson.annotation.JsonIgnore private String taskDefinitionKey;
    private String taskId; // Workflow task id
    private String assignee; // Task assignee
    private String actName; // Task name
}
