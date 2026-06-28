/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.medcas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 4401 icuinfo 行。
 */
@Data
public class MedcasHomepageIcuInfo {

    @JsonProperty("icu_codeid")
    private String icuCodeid;

    @JsonProperty("inpool_icu_time")
    private String inpoolIcuTime;

    @JsonProperty("out_icu_time")
    private String outIcuTime;

    @JsonProperty("vali_flag")
    private String valiFlag;
}
