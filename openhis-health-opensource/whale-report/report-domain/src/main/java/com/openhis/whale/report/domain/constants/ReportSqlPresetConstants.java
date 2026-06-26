package com.openhis.whale.report.domain.constants;

import java.util.regex.Pattern;

/**
 * 平台 SQL 预制报表目录相关常量。
 */
public final class ReportSqlPresetConstants {

    /** 从 remark 中解析优先级标记（如「· P2 ·」）。 */
    public static final Pattern PRESET_PRIORITY = Pattern.compile("·\\s*P([23])\\s*·");

    private ReportSqlPresetConstants() {
    }
}
