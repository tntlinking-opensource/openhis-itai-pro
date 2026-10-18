/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.medcas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 【4101A】icuinfo 重症监护行。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MedcasSettleListIcuInfo {

    /** 重症监护病房类型（字典 ICU_TYPE：1 CCU…7 ICU / 9 其他）。 */
    @JsonProperty("scs_cutd_ward_type")
    private String scsCutdWardType;

    /** 进入重症监护时间（yyyy-MM-dd HH:mm:ss）。 */
    @JsonProperty("scs_cutd_inpool_time")
    private String scsCutdInpoolTime;

    /** 退出重症监护时间（yyyy-MM-dd HH:mm:ss）。 */
    @JsonProperty("scs_cutd_exit_time")
    private String scsCutdExitTime;

    /** 合计时长（格式 {@code 天/时/分}，例 {@code 1/13/24}）。 */
    @JsonProperty("scs_cutd_sum_dura")
    private String scsCutdSumDura;
}
