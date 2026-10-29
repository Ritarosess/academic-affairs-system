package com.school.edu.common;

/**
 * 学业预警等级。severity 越大越严重，最终等级取各条规则中最严重的一条。
 */
public enum WarningLevel {

    NORMAL(0, "学业正常", "success"),
    YELLOW(1, "黄色预警（学业关注）", "warning"),
    ORANGE(2, "橙色预警（学业预警）", "warning"),
    RED(3, "红色预警（学业红线）", "danger");

    private final int severity;
    private final String label;
    /** 前端 Element Plus 标签类型提示 */
    private final String tagType;

    WarningLevel(int severity, String label, String tagType) {
        this.severity = severity;
        this.label = label;
        this.tagType = tagType;
    }

    public int getSeverity() {
        return severity;
    }

    public String getLabel() {
        return label;
    }

    public String getTagType() {
        return tagType;
    }

    public boolean isTriggered() {
        return this != NORMAL;
    }

    public static WarningLevel max(WarningLevel a, WarningLevel b) {
        if (a == null) {
            return b == null ? NORMAL : b;
        }
        if (b == null) {
            return a;
        }
        return a.severity >= b.severity ? a : b;
    }

    public static WarningLevel of(String name) {
        if (name == null || name.isBlank()) {
            return NORMAL;
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
