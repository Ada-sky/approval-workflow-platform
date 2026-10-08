package com.ada.approval.query;

import lombok.Data;

import java.io.Serializable;

@Data
public class EmployeeQuery extends BaseQuery implements Serializable {
    private String empName; // Employee name
    private Integer deptId; // Department id
    private String empNum; // Employee number
}
