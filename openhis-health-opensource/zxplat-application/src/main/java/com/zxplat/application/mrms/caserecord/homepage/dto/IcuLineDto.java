/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.homepage.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 首页重症监护行 DTO（终改真源 {@code mrms_homepage_icu}；对齐老 IN_HOS_ICUINFO）。
 */
@Getter
@Setter
public class IcuLineDto {

    /** 行主键。 */
    private Long id;

    /** 排序号。 */
    private Integer sortNo;

    /** 重症监护病房类型（ICU_TYPE）。 */
    private String scsCutdWardType;

    /** 进入时间。 */
    private LocalDateTime scsCutdInpoolTime;

    /** 退出时间。 */
    private LocalDateTime scsCutdExitTime;

    /** 合计时长（天/时/分）。 */
    private String scsCutdSumDura;

    /** 有效标志。 */
    private String valiFlag;
}
