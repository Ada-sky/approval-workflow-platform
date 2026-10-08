package com.ada.approval.workflow;

import java.util.List;

/**
 * English workflow titles with read compatibility for historical persisted names. Legacy aliases
 * are intentionally escaped and never used as API display values. No database rewrite is performed;
 * remove aliases only after an explicit data migration.
 */
public final class WorkflowTitleNames {
    public static final String GENERAL_MANAGER = "General Manager";
    public static final String HR = "HR";
    private static final String LEGACY_GENERAL_MANAGER = "\u603b\u7ecf\u7406";
    private static final String LEGACY_HR = "\u4eba\u4e8b";

    private WorkflowTitleNames() {}

    public static List<String> generalManagerNames() {
        return List.of(GENERAL_MANAGER, LEGACY_GENERAL_MANAGER);
    }

    public static List<String> hrNames() {
        return List.of(HR, LEGACY_HR);
    }

    public static String display(String name) {
        if (generalManagerNames().contains(name == null ? "" : name)) return GENERAL_MANAGER;
        if (hrNames().contains(name == null ? "" : name)) return HR;
        return name;
    }

    /** Seed comparison accepts known historical names without rewriting existing rows. */
    public static boolean equivalent(String expected, String actual) {
        return java.util.Objects.equals(display(expected), display(actual));
    }
}
