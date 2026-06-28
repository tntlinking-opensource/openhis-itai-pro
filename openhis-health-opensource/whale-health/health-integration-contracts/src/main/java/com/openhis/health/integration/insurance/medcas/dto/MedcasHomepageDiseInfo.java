/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.medcas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 4401 diseinfo 行。
 */
@Data
public class MedcasHomepageDiseInfo {

    @JsonProperty("ipt_medcas_hmpg_sn")
    private String iptMedcasHmpgSn;

    @JsonProperty("mdtrt_sn")
    private String mdtrtSn;

    @JsonProperty("fixmedins_code")
    private String fixmedinsCode;

    @JsonProperty("ipt_patn_disediag_type_code")
    private String iptPatnDisediagTypeCode;

    @JsonProperty("maindiag_flag")
    private String maindiagFlag;

    @JsonProperty("diag_code")
    private String diagCode;

    @JsonProperty("diag_name")
    private String diagName;

    @JsonProperty("adm_cond_code")
    private String admCondCode;

    @JsonProperty("vali_flag")
    private String valiFlag;
}
