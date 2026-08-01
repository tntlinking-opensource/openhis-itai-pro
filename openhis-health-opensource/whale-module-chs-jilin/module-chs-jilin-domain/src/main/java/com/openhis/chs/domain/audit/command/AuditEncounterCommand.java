/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.audit.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 3101 就诊节点（fsi_encounter_dtos 元素） */
@Data
@Accessors(chain = true)
public class AuditEncounterCommand {

    /** 医保就诊 ID */
    private String mdtrtId;
    /** 医疗机构编码 */
    private String medinsId;
    /** 医疗机构名称 */
    private String medinsName;
    /** 医疗机构医保区划（须与定点归属一致） */
    private String medinsAdmdvs;
    /** 医疗机构类型 */
    private String medinsType;
    /** 医疗机构等级 */
    private String medinsLv;
    /** 病区编码 */
    private String wardareaCodg;
    /** 病房号 */
    private String wardno;
    /** 床号 */
    private String bedno;
    /** 入院/就诊时间 */
    private String admDate;
    /** 出院/结束时间 */
    private String dscgDate;
    /** 主诊编码 */
    private String dscgMainDiseCodg;
    /** 主诊名称 */
    private String dscgMainDiseName;
    /** 医师编码 */
    private String drCodg;
    /** 入院科室编码 */
    private String admDeptCodg;
    /** 入院科室名称 */
    private String admDeptName;
    /** 出院科室编码 */
    private String dscgDeptCodg;
    /** 出院科室名称 */
    private String dscgDeptName;
    /** 就诊类型（门诊/住院/购药） */
    private String medMdtrtType;
    /** 医疗类别 */
    private String medType;
    /** 生育状态 */
    private String matnStas;
    /** 医疗费总额 */
    private BigDecimal medfeeSumamt;
    /** 全自费金额 */
    private BigDecimal ownpayAmt;
    /** 自付金额 */
    private BigDecimal selfpayAmt;
    /** 账户支付 */
    private BigDecimal acctPayamt;
    /** 救助金额 */
    private BigDecimal maAmt;
    /** 基金支付 */
    private BigDecimal hifpPayamt;
    /** 结算次数 */
    private Integer setlTotlnum;
    /** 险种 */
    private String insutype;
    /** 报销标志 */
    private String reimFlag;
    /** 异地结算标志 */
    private String outSetlFlag;

    /** 诊断集合 */
    private List<AuditDiagnoseCommand> diagnoses = new ArrayList<>();
    /** 医嘱/费用集合 */
    private List<AuditOrderCommand> orders = new ArrayList<>();
    /** 手术集合（可空列表） */
    private List<AuditOperationCommand> operations = new ArrayList<>();
}
