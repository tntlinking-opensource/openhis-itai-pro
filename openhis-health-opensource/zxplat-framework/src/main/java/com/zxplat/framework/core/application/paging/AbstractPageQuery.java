/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application.paging;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 分页读查询通用基类（B.10.11）：分页键 {@code pageNo}/{@code pageSize}、关键字 {@code keyword}、可选时段、可选排序与 where
 * 白名单。
 *
 * <p>原落在 {@code domain.share.query}；为供 peis/pacs 等插件不依赖 HIS application，真身迁入 framework。领域侧仍保留同名
 * 子类作源码兼容。</p>
 *
 * <p>{@link #springPageIndex()} 返回 Spring Data {@code Pageable} 所需的 <strong>0-based</strong> 页下标（由 1-based
 * {@link #pageNo} 推导）。</p>
 * <p>{@link #getBoundedPageSize()} 提供默认与上限保护；{@link #sortDirection} 未传时默认为 {@code DESC}。</p>
 */
@Getter
@Setter
public class AbstractPageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页码（从 1 开始；可不传）。 */
    private Integer pageNo;

    /** 每页条数（可不传）。 */
    private Integer pageSize;

    /** 关键字（语义由各读模型查询解释）。 */
    private String keyword;

    /** 时间范围下限（含）；常与 {@link #endDate} 成对。 */
    private LocalDateTime startDate;

    /** 时间范围上限（含）。 */
    private LocalDateTime endDate;

    /** 可选排序字段（是否生效由具体查询与白名单决定）。 */
    private String sortBy;

    /**
     * 排序方向（如 ASC/DESC）；未显式传入时默认可按降序理解（与常见列表默认一致），具体业务仍可覆盖解释。
     */
    private String sortDirection = "DESC";

    /** 允许参与关键字/排序解析的属性名白名单（可选）。 */
    private List<String> where;

    /**
     * 由 1-based {@link #pageNo} 推导 Spring Data 所需的 0-based 页下标。
     *
     * @return 0-based；{@code pageNo} 未传或小于 1 时返回 0
     */
    public int springPageIndex() {
        Integer num = getPageNo();
        if (num == null || num < 1) {
            return 0;
        }
        return num - 1;
    }

    /**
     * 默认 10、下限 1、上限 1000（与历史列表分页防护对齐）。
     *
     * @return 有效页大小
     */
    public int getBoundedPageSize() {
        Integer s = getPageSize();
        if (s == null || s < 1) {
            return 10;
        }
        return Math.min(s, 1000);
    }
}
