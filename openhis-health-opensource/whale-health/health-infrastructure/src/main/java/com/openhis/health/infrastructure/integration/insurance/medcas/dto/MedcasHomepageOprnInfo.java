/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.medcas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 4401 病案首页 oprninfo 手术行。
 */
@Data
public class MedcasHomepageOprnInfo {

    /** 住院病案首页流水号。 */
    @JsonProperty("ipt_medcas_hmpg_sn")
    private String iptMedcasHmpgSn;

    /** 就医流水号。 */
    @JsonProperty("mdtrt_sn")
    private String mdtrtSn;

    /** 定点医药机构编号。 */
    @JsonProperty("fixmedins_code")
    private String fixmedinsCode;

    /** 手术操作日期。 */
    @JsonProperty("oprn_oprt_date")
    private String oprnOprtDate;

    /** 手术操作编码。 */
    @JsonProperty("oprn_oprt_code")
    private String oprnOprtCode;

    /** 手术操作名称。 */
    @JsonProperty("oprn_oprt_name")
    private String oprnOprtName;

    /** 主手术标志。 */
    @JsonProperty("main_oprn_flag")
    private String mainOprnFlag;

    /** 手术级别代码。 */
    @JsonProperty("oprn_lv_code")
    private String oprnLvCode;

    /** 手术切口愈合等级代码。 */
    @JsonProperty("sinc_heal_lv_code")
    private String sincHealLvCode;

    /** 麻醉方法代码。 */
    @JsonProperty("anst_mtd_code")
    private String anstMtdCode;

    /** 手术者姓名。 */
    @JsonProperty("oper_name")
    private String operName;

    /** 麻醉医师姓名。 */
    @JsonProperty("anst_dr_name")
    private String anstDrName;

    /** 有效标志。 */
    @JsonProperty("vali_flag")
    private String valiFlag;
}
