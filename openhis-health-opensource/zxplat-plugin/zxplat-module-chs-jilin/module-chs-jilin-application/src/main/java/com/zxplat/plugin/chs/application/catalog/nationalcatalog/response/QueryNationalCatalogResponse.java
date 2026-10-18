/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.catalog.nationalcatalog.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 国家医保目录分页查询结果（UI 出参）。
 * <p>含分页元数据与当前页目录行摘要列表。</p>
 */
@Data
@Accessors(chain = true)
public class QueryNationalCatalogResponse {

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 起） */
    private int pageNo;

    /** 每页条数 */
    private int pageSize;

    /** 目录行列表 */
    private List<NationalCatalogItemResponse> rows = new ArrayList<>();
}
