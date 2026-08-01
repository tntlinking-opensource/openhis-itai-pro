/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.queries;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 国家医保目录镜像分页查询入参 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NationalCatalogPageQuery extends ChsCatalogPageQuery {

    /** 费用性质/契约主键 */
    private Long contractId;

    /** 租户主键 */
    private Long tenantId;

    /** 目录类别 list_type */
    private String listType;

    /** 医保目录编码 hilist_code（前缀匹配 */
    private String hilistCode;

    private String sourceInfno;

    private boolean dedupeByHilistCode;

    private String valiFlag;

    private String aprvno;

    private String spec;

    /** 选码：仅 1312 摘要行（source_line_no */
    private boolean pickerMode1312Only;

    /** 选码/检索：1301 文件明细行（含批准文号、规格、厂家） */
    private boolean pickerMode1301Detail;

    /** 选码：排除平台已过期（enddate 已过） */
    private boolean excludePlatformExpired;
}
