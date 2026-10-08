package com.ada.approval.query;

import lombok.Data;

@Data
public class MyTaskQuery extends BaseQuery {
    private String userName; // Authenticated username
    private String processDefinitionKey; // Process definition key
    private String title;
}
