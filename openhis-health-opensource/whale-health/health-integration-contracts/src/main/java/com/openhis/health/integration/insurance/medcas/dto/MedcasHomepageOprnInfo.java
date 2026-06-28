/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.medcas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 4401 oprninfo 行。
 */
@Data
public class MedcasHomepageOprnInfo {

    @JsonProperty("ipt_medcas_hmpg_sn")
    private String iptMedcasHmpgSn;

    @JsonProperty("mdtrt_sn")
    private String mdtrtSn;

    @JsonProperty("fixmedins_code")
    private String fixmedinsCode;

    @JsonProperty("oprn_oprt_date")
    private String oprnOprtDate;

    @JsonProperty("oprn_oprt_code")
    private String oprnOprtCode;

    @JsonProperty("oprn_oprt_name")
    private String oprnOprtName;

    @JsonProperty("main_oprn_flag")
    private String mainOprnFlag;

    @JsonProperty("vali_flag")
    private String valiFlag;
}
