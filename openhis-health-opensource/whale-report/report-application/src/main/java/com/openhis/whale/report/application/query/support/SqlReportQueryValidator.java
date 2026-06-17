package com.openhis.whale.report.application.query.support;

import com.openhis.whale.core.application.exception.ValidationException;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * C 类 SQL 报表只读校验（防注入 / 防写操作）。
 */
public final class SqlReportQueryValidator {

    private static final Pattern FORBIDDEN =
            Pattern.compile("(?is)\\b(insert|update|delete|drop|truncate|alter|create|grant|revoke|exec|execute|merge|call)\\b");

    private SqlReportQueryValidator() {}

    public static void validate(String sql) {
        if (!StringUtils.hasText(sql)) {
            throw new ValidationException("SQL 查询语句不能为空");
        }
        String trimmed = sql.trim();
        if (!trimmed.regionMatches(true, 0, "select", 0, 6)) {
            throw new ValidationException("仅允许 SELECT 查询语句");
        }
        if (trimmed.contains(";")) {
            throw new ValidationException("不允许包含分号或多语句 SQL");
        }
        if (FORBIDDEN.matcher(trimmed).find()) {
            throw new ValidationException("SQL 包含禁止的关键字");
        }
    }
}
