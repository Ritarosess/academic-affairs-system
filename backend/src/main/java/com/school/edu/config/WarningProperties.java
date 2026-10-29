package com.school.edu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * 学业红线预警阈值配置（对应 application.yml 的 app.warning）。
 * 阈值外置于配置文件，便于教务处按学校规定调整，避免硬编码。
 *
 * <p>判定方向：不及格学分与不及格门数均为「越大越严重」（≥ 阈值触发）。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.warning")
public class WarningProperties {

    private Level yellow = Level.of(3, 2);
    private Level orange = Level.of(6, 3);
    private Level red = Level.of(10, 4);

    @Data
    public static class Level {
        /** 累计不及格学分阈值（≥ 触发） */
        private BigDecimal failedCredits;
        /** 单学期最多不及格门数阈值（≥ 触发） */
        private Integer semesterFailedCount;

        public static Level of(double failedCredits, int semesterFailedCount) {
            Level level = new Level();
            level.setFailedCredits(BigDecimal.valueOf(failedCredits));
            level.setSemesterFailedCount(semesterFailedCount);
            return level;
        }
    }
}
