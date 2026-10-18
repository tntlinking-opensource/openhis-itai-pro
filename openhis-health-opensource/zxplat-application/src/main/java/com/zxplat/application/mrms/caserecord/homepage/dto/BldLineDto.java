/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.homepage.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 首页输血行 DTO（终改真源 {@code mrms_homepage_bld}；对齐老 IN_HOS_BLDINFO）。
 */
@Getter
@Setter
public class BldLineDto {

    /** 行主键。 */
    private Long id;

    /** 排序号。 */
    private Integer sortNo;

    /** 输血品种（bld_cat_code）。 */
    private String bldCat;

    /** 输血量。 */
    private String bldAmt;

    /** 输血计量单位。 */
    private String bldUnt;

    /** 有效标志。 */
    private String valiFlag;
}
