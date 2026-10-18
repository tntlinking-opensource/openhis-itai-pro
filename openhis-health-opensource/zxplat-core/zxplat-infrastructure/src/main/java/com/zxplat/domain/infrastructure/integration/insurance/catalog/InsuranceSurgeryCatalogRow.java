/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.catalog;

import lombok.Getter;
import lombok.Setter;

/**
 * 医保手术操作目录镜像行（1308）。
 */
@Getter
@Setter
public class InsuranceSurgeryCatalogRow {

    /** 医保手术操作编码（hilist_code / 1308 第 11 列）。 */
    private String hilistCode;

    /** 医保手术操作名称（hilist_name / 1308 第 12 列）。 */
    private String hilistName;

    /** 拼音助记（可空）。 */
    private String pinyin;

    /**
     * 1308 第 16 列临床版手术操作代码；空或占位表示文件未给临床版。
     * 仅非空时作为国临码真源，可生成 {@code clinical_proc_v3} 行；空时不得用医保码冒充。
     */
    private String clinicCode;

    /**
     * 1308 第 17 列临床版手术操作名称；生成国临行时作名称，空则回落第 12 列。
     */
    private String clinicName;
}
