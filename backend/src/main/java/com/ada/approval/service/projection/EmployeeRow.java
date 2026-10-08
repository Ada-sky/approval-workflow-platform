package com.ada.approval.service.projection;

import com.ada.approval.entity.Employee;
import lombok.Data;

import java.io.Serializable;

@Data
public class EmployeeRow extends Employee implements Serializable {
    private String userName; // Username
    private String deptName; // Department name
    private String titleName; // Job title name
    private String empStatus; // Employee status
}
