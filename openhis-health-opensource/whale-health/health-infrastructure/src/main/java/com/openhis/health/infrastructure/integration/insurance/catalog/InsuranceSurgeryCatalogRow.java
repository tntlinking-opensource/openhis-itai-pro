/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.catalog;

import lombok.Getter;
import lombok.Setter;

/**
 * 医保手术操作目录镜像行（1308）。
 */
@Getter
@Setter
public class InsuranceSurgeryCatalogRow {

    /** 医保手术操作编码（hilist_code）。 */
    private String hilistCode;

    /** 医保手术操作名称（hilist_name）。 */
    private String hilistName;

    /** 拼音助记（可空）。 */
    private String pinyin;
}
