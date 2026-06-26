package com.openhis.whale.report.domain.support;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.StringUtils;

import java.util.regex.Pattern;

/**
 * C 类 SQL 报表只读校验（防注入 / 防写操作 / 防 UNION 绕过）。
 */
public final class ReportQueryValidator {

    private static final Pattern FORBIDDEN =
            Pattern.compile(
                    "(?is)\\b(insert|update|delete|drop|truncate|alter|create|grant|revoke|exec|execute|"
                            + "merge|call|copy|into|union|intersect|except|pg_sleep|pg_read_file|"
                            + "lo_import|lo_export|dblink|pg_advisory_lock)\\b");

    private ReportQueryValidator() {}

    /**
     * 校验设计器/用户提交的只读 SQL。
     *
     * @param sql SELECT 或 WITH 单语句
     * @throws com.openhis.whale.core.application.exception.ValidationException 违反白名单时
     */
    public static void validate(String sql) {
        if (!StringUtils.isNotEmpty(sql)) {
            throw new ValidationException("SQL 查询语句不能为空");
        }
        String trimmed = sql.trim();
        // 禁止多语句，防止拼接写操作
        if (trimmed.contains(";")) {
            throw new ValidationException("不允许包含分号或多语句 SQL");
        }
        String executable = stripLeadingComments(trimmed);
        if (!StringUtils.isNotEmpty(executable)) {
            throw new ValidationException("SQL 查询语句不能为空");
        }
        // 仅允许只读查询入口
        if (!executable.regionMatches(true, 0, "select", 0, 6)
                && !executable.regionMatches(true, 0, "with", 0, 4)) {
            throw new ValidationException("仅允许 SELECT / WITH 查询语句");
        }
        String withoutComments = stripAllComments(trimmed);
        if (FORBIDDEN.matcher(withoutComments).find()) {
            throw new ValidationException("SQL 包含禁止的关键字");
        }
    }

    /** 跳过前导空白与注释，定位首条可执行语句。 */
    static String stripLeadingComments(String sql) {
        int index = 0;
        final int length = sql.length();
        while (index < length) {
            while (index < length && Character.isWhitespace(sql.charAt(index))) {
                index++;
            }
            if (index >= length) {
                break;
            }
            if (startsWith(sql, index, "--")) {
                index += 2;
                while (index < length && sql.charAt(index) != '\n' && sql.charAt(index) != '\r') {
                    index++;
                }
                continue;
            }
            if (startsWith(sql, index, "/*")) {
                index += 2;
                while (index + 1 < length && !(sql.charAt(index) == '*' && sql.charAt(index + 1) == '/')) {
                    index++;
                }
                if (index + 1 < length) {
                    index += 2;
                }
                continue;
            }
            break;
        }
        return sql.substring(index).trim();
    }

    /** 移除全部 SQL 注释后再做关键字扫描，避免注释文本误触发。 */
    static String stripAllComments(String sql) {
        StringBuilder builder = new StringBuilder(sql.length());
        int index = 0;
        final int length = sql.length();
        while (index < length) {
            if (startsWith(sql, index, "--")) {
                index += 2;
                while (index < length && sql.charAt(index) != '\n' && sql.charAt(index) != '\r') {
                    index++;
                }
                continue;
            }
            if (startsWith(sql, index, "/*")) {
                index += 2;
                while (index + 1 < length && !(sql.charAt(index) == '*' && sql.charAt(index + 1) == '/')) {
                    index++;
                }
                if (index + 1 < length) {
                    index += 2;
                }
                continue;
            }
            builder.append(sql.charAt(index));
            index++;
        }
        return builder.toString();
    }

    private static boolean startsWith(String sql, int index, String token) {
        return sql.regionMatches(index, token, 0, token.length());
    }
}
