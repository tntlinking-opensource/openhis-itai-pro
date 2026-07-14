/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.queries;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 目录分页查询基类（与 health AbstractPageQuery 语义对齐，避免 chs-domain 依赖 health） */
@Getter
@Setter
public abstract class ChsCatalogPageQuery implements Serializable {

    private Integer pageNo;
    private Integer pageSize;
    private String keyword;

    /** Spring Data 页码（pageNo 从 1 开始） */
    public int springPageIndex() {
        Integer num = getPageNo();
        if (num == null || num < 1) {
            return 0;
        }
        return num - 1;
    }

    /** 分页大小，默认 10，上限 1000 */
    public int getBoundedPageSize() {
        Integer s = getPageSize();
        if (s == null || s < 1) {
            return 10;
        }
        return Math.min(s, 1000);
    }
}
