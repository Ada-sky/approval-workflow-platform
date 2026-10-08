package com.ada.approval.service.projection;

import lombok.Data;

@Data
public class MenuGrantNode {
    private Integer id;
    private Integer pId;
    private String name;
    private Boolean checked; // Checked indicates a granted permission; default is false
}
