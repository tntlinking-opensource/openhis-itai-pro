/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.coding.request;

import lombok.Getter;
import lombok.Setter;

/**
 * ICD 编码检索请求。
 *
 * <p>手术选码（{@code searchIcd9Cm3}）支持空关键字分页浏览；诊断选码仍建议带关键字。</p>
 */
@Getter
@Setter
public class SearchIcdRequest {

    /** 检索关键字；手术选码可空（打开下拉浏览）。 */
    private String keyword;

    /**
     * 最大返回条数（兼容旧调用）。
     *
     * <p>与 {@code pageSize} 同时出现时以 {@code pageSize} 为准。</p>
     */
    private Integer limit;

    /** 页码（从 1 起）；空则 1。 */
    private Integer pageNo;

    /** 每页条数；空则回落 {@code limit} 或默认 20。 */
    private Integer pageSize;
}
