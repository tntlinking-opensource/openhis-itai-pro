/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.model;

import lombok.Data;

/**
 * 文件头分页查询条件。
 */
@Data
public class PageNoticeDocQuery {

    /** 租户主键。 */
    private Long tenantId;

    /** 页码（从 1 开始）。 */
    private int page = 1;

    /** 每页条数。 */
    private int size = 20;

    /** 文件种类过滤。 */
    private String docKind;

    /** 品类过滤。 */
    private String category;

    /** 应用状态过滤。 */
    private String applyStatus;

    /** 标题/文件名关键字。 */
    private String keyword;

    /**
     * Spring Data 0-based 页码。
     *
     * @return 页索引
     */
    public int springPageIndex() {
        if (page < 1) {
            return 0;
        }
        return page - 1;
    }

    /**
     * 有界页大小，避免过大扫描。
     *
     * @return 1～200
     */
    public int boundedPageSize() {
        if (size < 1) {
            return 20;
        }
        if (size > 200) {
            return 200;
        }
        return size;
    }
}
