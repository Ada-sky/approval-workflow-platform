package com.ada.approval.query;

import lombok.Data;

import java.io.Serializable;

@Data
public class RoleQuery extends BaseQuery implements Serializable {
    private String roleName; // Role name
    private Integer status; // Status
}
