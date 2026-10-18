/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.catalog;

import lombok.Getter;
import lombok.Setter;

/**
 * 医保疾病/诊断类目录镜像行（1307/1309/1314/1315 等）。
 */
@Getter
@Setter
public class InsuranceDiseaseCatalogRow {

    /** 医保目录编码（hilist_code）。 */
    private String hilistCode;

    /** 医保目录名称（hilist_name）。 */
    private String hilistName;

    /** 拼音助记（可空）。 */
    private String pinyin;
}
