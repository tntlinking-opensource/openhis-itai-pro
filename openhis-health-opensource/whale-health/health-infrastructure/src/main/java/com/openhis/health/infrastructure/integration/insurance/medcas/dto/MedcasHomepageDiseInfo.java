/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.medcas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 4401 病案首页 diseinfo 诊断行。
 */
@Data
public class MedcasHomepageDiseInfo {

    /** 住院病案首页流水号。 */
    @JsonProperty("ipt_medcas_hmpg_sn")
    private String iptMedcasHmpgSn;

    /** 就医流水号。 */
    @JsonProperty("mdtrt_sn")
    private String mdtrtSn;

    /** 定点医药机构编号。 */
    @JsonProperty("fixmedins_code")
    private String fixmedinsCode;

    /** 住院患者疾病诊断类型 code。 */
    @JsonProperty("ipt_patn_disediag_type_code")
    private String iptPatnDisediagTypeCode;

    /** 主诊断标志（1 主诊断）。 */
    @JsonProperty("maindiag_flag")
    private String maindiagFlag;

    /** 诊断编码（ICD）。 */
    @JsonProperty("diag_code")
    private String diagCode;

    /** 诊断名称。 */
    @JsonProperty("diag_name")
    private String diagName;

    /** 入院病情 code。 */
    @JsonProperty("adm_cond_code")
    private String admCondCode;

    /** 有效标志。 */
    @JsonProperty("vali_flag")
    private String valiFlag;
}
