/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.settle.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工伤结算编排上下文（bridge {@link com.zxplat.plugin.wi.bridge.context.ChsWorkInjurySettleContextLoader} 从 HIS 加载）。
 *
 * <p>供 {@link com.zxplat.plugin.wi.gateway.scheme.shared.assembler.WiPlatformInputAssembler} 组装 2201～2207。</p>
 */
@Data
@Accessors(chain = true)
public class WiSettleContext {

    /** 工伤费用性质 contract id。 */
    private Long contractId;
    /** 就诊 id。 */
    private Long encounterId;

    /** 门诊/住院流水号 ipt_otp_no（= HIS encounter 流水号）。 */
    private String iptOtpNo;

    /** 1101/9102 人员编号。 */
    private String psnNo;
    /** 9102/2301 审批资格 ID。 */
    private String qualificationId;
    /** 2301 工伤认定 ID。 */
    private String injureAffirmId;

    /** 医疗类别 med_type（默认见 WiPlatformConstants）。 */
    private String medType;
    /** 入院/就诊时间 adm_time。 */
    private LocalDateTime admTime;

    /** 入院/门诊诊断编码。 */
    private String admDiagCode;
    /** 入院/门诊诊断名称（HIS 展示，不上报）。 */
    private String admDiagName;

    /** 科室编码 adm_dept_codg。 */
    private String deptCode;
    /** 科室名称 wardarea_name。 */
    private String deptName;
    /** 主治医生编码 atddr_no。 */
    private String atddrNo;
    /** 主治医生姓名（HIS 展示）。 */
    private String drName;
    /** 患者联系电话 tel。 */
    private String patientTel;
    /** 患者姓名（HIS 展示，写 yb_settlement_record.psn_name）。 */
    private String patientName;

    /** 2204 收费明细行。 */
    private List<WiSettleChargeLine> chargeLines;
    /** 2206 医疗费总额（HIS 汇总，可与平台返回比对）。 */
    private BigDecimal medfeeSumamt;

    /** 收银草稿 id（OP_SETTLE）。 */
    private Long paymentDraftId;
    /** 操作员 id。 */
    private Long operatorId;
    /** 租户 id。 */
    private Long tenantId;
}
