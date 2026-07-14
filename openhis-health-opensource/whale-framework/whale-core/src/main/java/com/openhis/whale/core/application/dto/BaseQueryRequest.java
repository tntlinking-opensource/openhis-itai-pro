/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.dto;

import lombok.Data;

import java.util.List;

/**
 * 基础查询请求DTO
 */
@Data
public class BaseQueryRequest {

    /**
     * 页码（从0开始）
     */
    private Integer pageNo = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 10;

    /**
     * 排序字段
     */
    private String sortBy;

    /**
     * 排序方向（ASC/DESC）
     */
    private String sortDirection = "DESC";

    /**
     * 关键字搜索
     */
    private String searchKey;

    /**
     * 关键字搜索
     */
    private List<String> where;

    /**
     * 获取有效的页码
     * @return 页码（至少为0）
     */
    public int getPageNo() {

        return pageNo != null && pageNo >= 0 ? pageNo-1 : 0;
    }

    /**
     * 获取有效的每页大小
     * @return 每页大小（1-100之间）
     */
    public int getPageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 1000); // 最大100
    }
}
