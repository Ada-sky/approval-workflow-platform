package com.ada.approval.query;

import lombok.Data;

import java.io.Serializable;

@Data
public class BaseQuery implements Serializable {
    private Integer page; // Current page
    private Integer limit; // Page size
}
