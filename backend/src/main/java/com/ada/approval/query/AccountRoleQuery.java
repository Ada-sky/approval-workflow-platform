package com.ada.approval.query;

import lombok.Data;

import java.io.Serializable;

@Data
public class AccountRoleQuery extends BaseQuery implements Serializable {
    private Integer roleId; // Role id
    private String empName;
    private String empNum;
}
