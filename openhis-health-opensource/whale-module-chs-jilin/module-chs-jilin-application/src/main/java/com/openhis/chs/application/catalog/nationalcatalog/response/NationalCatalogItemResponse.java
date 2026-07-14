/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.catalog.nationalcatalog.response;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 国家医保目录单行摘要（UI 出参）。
 * <p>仅含列表展示所需字段；完整字段见 {@link com.openhis.chs.domain.catalog.result.NationalCatalogItemResult}。</p>
 */
@Data
@Accessors(chain = true)
public class NationalCatalogItemResponse {

    /** 目录类别 list_type */
    private String listType;

    /** 医保目录编码 hilist_code */
    private String hilistCode;

    /** 医保目录名称 hilist_name */
    private String hilistName;

    /** 收费项目等级（01/02/03 或平台中文「甲类」等） */
    private String chrgitmLv;

    /** 有效标志 */
    private String valiFlag;
}
