/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.gateway.scheme.settle.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【2201】门诊/住院登记 input（10-P0 §6.1；收费链首步）。
 */
@Data
@Accessors(chain = true)
public class Wi2201Input {

    /** 门诊/住院流水号；anchor 生成，贯穿 2201～2208。 */
    @JSONField(name = "ipt_otp_no")
    private String iptOtpNo;

    /** 医疗类别 med_type；默认 12。 */
    @JSONField(name = "med_type")
    private String medType;

    /** 入院/就诊时间 yyyyMMddHHmmss。 */
    @JSONField(name = "adm_time")
    private String admTime;

    /** 入院诊断描述；主诊断 ICD。 */
    @JSONField(name = "adm_diag_dscr")
    private String admDiagDscr;

    /** 病区/科室名称。 */
    @JSONField(name = "wardarea_name")
    private String wardareaName;

    /** 入院科室编码 adm_dept_codg。 */
    @JSONField(name = "adm_dept_codg")
    private String admDeptCodg;

    /** 床位；门诊传 "-"。 */
    @JSONField(name = "adm_bed")
    private String admBed;

    /** 主治医生编码 atddr_no。 */
    @JSONField(name = "atddr_no")
    private String atddrNo;

    /** 联系电话；缺省 "0"。 */
    @JSONField(name = "tel")
    private String tel;

    /** 人员编号 psn_no。 */
    @JSONField(name = "psn_no")
    private String psnNo;

    /** 住院号；门诊传空。 */
    @JSONField(name = "ipt_no")
    private String iptNo;

    /** 9102 所选审批资格 ID；OP_SETTLE 必填。 */
    @JSONField(name = "qualification_id")
    private String qualificationId;
}
