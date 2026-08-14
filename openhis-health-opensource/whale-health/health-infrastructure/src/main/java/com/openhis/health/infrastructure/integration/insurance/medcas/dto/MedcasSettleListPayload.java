/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.medcas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 【4101A】结算清单上传载荷。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MedcasSettleListPayload {

    /** 结算清单信息（单行）。 */
    @JsonProperty("setlinfo")
    private MedcasSettleListSetlinfo setlinfo;

    /** 门诊慢特病诊断（可空数组）。 */
    @JsonProperty("opspdiseinfo")
    private List<Object> opspdiseinfo = new ArrayList<>();

    /** 住院诊断。 */
    @JsonProperty("diseinfo")
    private List<MedcasSettleListDiseInfo> diseinfo = new ArrayList<>();

    /** 手术操作。 */
    @JsonProperty("oprninfo")
    private List<MedcasSettleListOprnInfo> oprninfo = new ArrayList<>();

    /** 重症监护（无数据则空数组）。 */
    @JsonProperty("icuinfo")
    private List<Object> icuinfo = new ArrayList<>();

    /** 输血（无数据则空数组）。 */
    @JsonProperty("bldinfo")
    private List<Object> bldinfo = new ArrayList<>();
}
