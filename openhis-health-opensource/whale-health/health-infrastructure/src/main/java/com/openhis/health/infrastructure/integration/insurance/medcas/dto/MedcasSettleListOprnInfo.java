/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.medcas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 【4101A】oprninfo 手术行。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MedcasSettleListOprnInfo {

    /** 手术操作类别。 */
    @JsonProperty("oprn_oprt_type")
    private String oprnOprtType;

    /** 手术操作名称。 */
    @JsonProperty("oprn_oprt_name")
    private String oprnOprtName;

    /** 手术操作代码（医保码）。 */
    @JsonProperty("oprn_oprt_code")
    private String oprnOprtCode;

    /** 麻醉方式。 */
    @JsonProperty("anst_way")
    private String anstWay;

    /** 术者医师代码。 */
    @JsonProperty("oper_dr_code")
    private String operDrCode;

    /** 麻醉医师代码。 */
    @JsonProperty("anst_dr_code")
    private String anstDrCode;

    /** 手术操作开始时间。 */
    @JsonProperty("oprn_oprt_begntime")
    private String oprnOprtBegntime;
}
