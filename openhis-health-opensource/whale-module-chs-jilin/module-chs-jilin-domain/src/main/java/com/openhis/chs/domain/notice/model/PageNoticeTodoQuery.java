/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.model;

import lombok.Data;

/**
 * 待办分页查询。
 */
@Data
public class PageNoticeTodoQuery {
    /** 租户主键。 */
    private Long tenantId;
    /** 页码（从 1 开始）；空或小于 1 时按 1。 */
    private Integer page;
    /** 每页条数；空或小于 1 时按 20，上限 200。 */
    private Integer size;
    /** 待办类型过滤（STOP / PAY_CLASS）；空表示不限。 */
    private String todoType;
    /** 待办状态过滤；空时 QueryService 默认 OPEN。 */
    private String status;

    /**
     * 转为 Spring Data 0-based 页下标。
     *
     * @return 页下标
     */
    public int springPageIndex() {
        int p = 1;
        if (page != null && page >= 1) {
            p = page;
        }
        return p - 1;
    }

    /**
     * 返回夹紧后的每页条数。
     *
     * @return 1～200
     */
    public int boundedPageSize() {
        int s = 20;
        if (size != null && size >= 1) {
            s = size;
        }
        return Math.min(s, 200);
    }
}
