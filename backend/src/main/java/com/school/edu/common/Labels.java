package com.school.edu.common;

/** 枚举值到中文展示文案的映射。 */
public final class Labels {

    private static final String[] WEEKDAYS = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private Labels() {
    }

    public static String weekday(Integer weekday) {
        if (weekday == null || weekday < 1 || weekday > 7) {
            return "";
        }
        return WEEKDAYS[weekday];
    }

    public static String role(String role) {
        if (role == null) {
            return "";
        }
        return switch (role) {
            case "ADMIN" -> "管理员";
            case "TEACHER" -> "教师";
            case "STUDENT" -> "学生";
            default -> role;
        };
    }

    public static String selectionStatus(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "SELECTED" -> "已选";
            case "DROPPED" -> "已退课";
            default -> status;
        };
    }

    /** 课程审核状态。 */
    public static String courseAudit(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "PENDING" -> "待审核";
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已拒绝";
            default -> status;
        };
    }
}
