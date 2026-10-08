package com.ada.approval.query;

import lombok.Data;

import java.io.Serializable;

@Data
public class HolidayApplyQuery extends BaseQuery implements Serializable {
    private String title; // Leave request title
    private String status; // Leave workflow status
    private String startTime; // Leave start date
    private String endTime; // Leave end date
    private String userId; // Current user's id
}
