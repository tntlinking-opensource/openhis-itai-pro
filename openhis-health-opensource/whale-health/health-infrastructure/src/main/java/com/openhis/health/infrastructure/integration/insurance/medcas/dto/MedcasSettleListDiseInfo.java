/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.medcas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 【4101A】diseinfo 诊断行。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MedcasSettleListDiseInfo {

    /** 诊断类别。 */
    @JsonProperty("diag_type")
    private String diagType;

    /** 诊断代码（医保码）。 */
    @JsonProperty("diag_code")
    private String diagCode;

    /** 诊断名称。 */
    @JsonProperty("diag_name")
    private String diagName;

    /** 入院病情类型。 */
    @JsonProperty("adm_cond_type")
    private String admCondType;

    /** 主诊断标志。 */
    @JsonProperty("maindiag_flag")
    private String maindiagFlag;
}
