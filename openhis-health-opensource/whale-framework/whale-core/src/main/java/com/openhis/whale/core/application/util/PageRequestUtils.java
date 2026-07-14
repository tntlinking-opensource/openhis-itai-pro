/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * 分页请求工具类
 */
public class PageRequestUtils {

    /**
     * 创建分页请求
     * @param pageIndex 页码（从0开始）
     * @param pageSize 每页大小
     * @return PageRequest
     */
    public static PageRequest create(int pageIndex, int pageSize) {
        return PageRequest.of(pageIndex, pageSize);
    }

    /**
     * 创建分页请求（带排序）
     * @param pageIndex 页码（从0开始）
     * @param pageSize 每页大小
     * @param direction 排序方向
     * @param properties 排序字段
     * @return PageRequest
     */
    public static PageRequest create(int pageIndex, int pageSize, Sort.Direction direction, String... properties) {
        return PageRequest.of(pageIndex, pageSize, Sort.by(direction, properties));
    }

    /**
     * 创建默认分页请求（按创建时间倒序）
     * @param pageIndex 页码（从0开始）
     * @param pageSize 每页大小
     * @return PageRequest
     */
    public static PageRequest createDefault(int pageIndex, int pageSize) {
        return PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
