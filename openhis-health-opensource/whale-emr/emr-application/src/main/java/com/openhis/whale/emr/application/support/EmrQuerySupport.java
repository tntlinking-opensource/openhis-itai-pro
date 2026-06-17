package com.openhis.whale.emr.application.support;

import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * EMR 应用层查询与字符串小工具（禁三元，跨 AppService 共用）。
 */
public final class EmrQuerySupport {

    public static final int DEFAULT_PAGE_SIZE = 20;

    private EmrQuerySupport() {
    }

    public static int resolvePageNo(Integer pageNo, Integer pageIndex) {
        if (pageNo != null && pageNo > 0) {
            return pageNo;
        }
        if (pageIndex != null && pageIndex > 0) {
            return pageIndex;
        }
        return 1;
    }

    public static int resolvePageSize(Integer pageSize) {
        if (pageSize != null && pageSize > 0) {
            return pageSize;
        }
        return DEFAULT_PAGE_SIZE;
    }

    public static String stringIdOrNull(Object id) {
        if (id == null) {
            return null;
        }
        return String.valueOf(id);
    }

    public static String trimOrEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    public static String trimOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public static String trimOrDefault(String value, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return value.trim();
    }

    public static Map<String, String> copyMetadataMap(Map<String, String> metadata) {
        if (metadata != null) {
            return new HashMap<>(metadata);
        }
        return new HashMap<>();
    }

    public static String nullToEmpty(String value) {
        if (value != null) {
            return value;
        }
        return "";
    }
}
