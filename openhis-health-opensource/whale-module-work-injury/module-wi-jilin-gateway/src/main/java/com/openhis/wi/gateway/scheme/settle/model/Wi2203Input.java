/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.settle.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【2203】登记信息修改 input（10-P0 §7；收费链第二步）。
 */
@Data
@Accessors(chain = true)
public class Wi2203Input {

    /** 门诊/住院流水号 ipt_otp_no。 */
    @JSONField(name = "ipt_otp_no")
    private String iptOtpNo;

    /** 入院/就诊时间 yyyyMMddHHmmss。 */
    @JSONField(name = "adm_time")
    private String admTime;

    /** 人员编号 psn_no。 */
    @JSONField(name = "psn_no")
    private String psnNo;

    /** 医疗类别；默认 12。 */
    @JSONField(name = "med_type")
    private String medType;

    /** 入院诊断描述。 */
    @JSONField(name = "adm_diag_dscr")
    private String admDiagDscr;

    /** 病区/科室名称 wardarea_name。 */
    @JSONField(name = "wardarea_name")
    private String wardareaName;

    /** 入院科室编码 adm_dept_codg。 */
    @JSONField(name = "adm_dept_codg")
    private String admDeptCodg;

    /** 床位 adm_bed；门诊传 "-"。 */
    @JSONField(name = "adm_bed")
    private String admBed;

    /** 主治医生编码 atddr_no。 */
    @JSONField(name = "atddr_no")
    private String atddrNo;
}
