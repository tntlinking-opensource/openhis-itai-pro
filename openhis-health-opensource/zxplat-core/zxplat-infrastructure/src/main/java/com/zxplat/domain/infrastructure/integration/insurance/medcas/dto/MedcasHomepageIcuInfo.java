/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.medcas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 4401 病案首页 icuinfo ICU 行。
 */
@Data
public class MedcasHomepageIcuInfo {

    /** ICU 类型 code。 */
    @JsonProperty("icu_codeid")
    private String icuCodeid;

    /** 进入 ICU 时间。 */
    @JsonProperty("inpool_icu_time")
    private String inpoolIcuTime;

    /** 退出 ICU 时间。 */
    @JsonProperty("out_icu_time")
    private String outIcuTime;

    /** 有效标志。 */
    @JsonProperty("vali_flag")
    private String valiFlag;
}
