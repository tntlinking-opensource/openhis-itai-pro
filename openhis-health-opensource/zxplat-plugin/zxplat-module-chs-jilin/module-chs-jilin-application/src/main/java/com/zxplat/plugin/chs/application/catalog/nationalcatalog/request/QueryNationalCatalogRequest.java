/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.catalog.nationalcatalog.request;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 国家医保目录分页查询（UI 入参）。
 * <p>对应 {@code InsuranceNationalCatalogAppService#queryNationalCatalogPage}，
 * 查询本地 {@code yb_national_catalog_item} 镜像。</p>
 */
@Data
@Accessors(chain = true)
public class QueryNationalCatalogRequest {

    /** 契约业务编号（与 contractId 二选一） */
    private String contractBusNo;

    /** 费用性质/契约主键 */
    private Long contractId;

    /** 页码（从 1 起） */
    private Integer pageNo;

    /** 每页条数 */
    private Integer pageSize;

    /** 关键字（编码/名称/Pinyin 等模糊匹配） */
    private String keyword;

    /** 目录类别 list_type */
    private String listType;

    /** 医保目录编码 hilist_code（前缀匹配） */
    private String hilistCode;

    /** 来源接口编号（1301/1312 等） */
    private String sourceInfno;

    /** 是否按 hilistCode 去重 */
    private Boolean dedupeByHilistCode;

    /** 有效标志 vali_flag */
    private String valiFlag;

    /** 批准文号模糊 */
    private String aprvno;

    /** 规格模糊 */
    private String spec;

    /** 选码：排除平台已过期 */
    private Boolean excludePlatformExpired;
}
