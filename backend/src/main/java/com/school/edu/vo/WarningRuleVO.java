package com.school.edu.vo;

import java.math.BigDecimal;

/**
 * 单条预警规则的判定结果，供前端逐条展示"哪条规则触发了、当前值是多少、阈值是多少"。
 */
public record WarningRuleVO(
        String code,
        String label,
        /** 当前值（已格式化为展示用字符串） */
        String value,
        /** 阈值说明 */
        String threshold,
        /** 本条规则的等级：NORMAL / YELLOW / ORANGE / RED */
        String level,
        String levelLabel,
        boolean triggered,
        String description) {
}
