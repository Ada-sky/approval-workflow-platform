package com.ada.approval.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the existing paginated adapter response consumed by workflow task-list mapping. It does
 * not query persistence or authorize rows.
 */
public class PageResultUtil {
    public static Map<String, Object> getResult(long total, List list) {
        Map<String, Object> map = new HashMap<>();
        map.put("code", 0); // The retained response format requires code 0
        map.put("msg", ""); // Leave the response message empty
        map.put("count", total); // The retained pagination format uses count for the total
        map.put("data", list); // The retained pagination format uses data for the result list
        return map;
    }
}
