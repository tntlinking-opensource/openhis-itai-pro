/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 门诊医保结算编排上下文（bridge HIS 上下文加载） */
@Data
@Accessors(chain = true)
public class OpPreSettleCommand {

    private Long contractId;
    private String contractBusNo;
    private Long encounterId;
    private String encounterBusNo;
    private Long patientId;
    private String patientName;
    private String patientCertNo;
    private String patientGender;
    private LocalDateTime encounterStartTime;
    private Long organizationId;
    private String deptCode;
    private String deptName;
    private String caty;
    private String atddrNo;
    private String drName;

    private String mdtrtCertType;
    private String mdtrtCertNo;
    private String psnNo;
    private String insuplcAdmdvs;
    private String insutype;
    /**
     * 出生日期（yyyy-MM-dd），供 3101 {@code patient_dtos.brdy}；
     * 由 ContextLoader 从 Encounter 患者上下文写入。
     */
    private String brdy;
    private String medType;

    /**
     * 是否挂号费预结算批次（默认诊断 / 失败自动 2202）。
     * <p>由 ContextLoader 按收费项生成来源或国标 med_type=12 判定；与实际上报 med_type（可与普通门诊同为 11）解耦。</p>
     */
    private Boolean registerFeePath;

    /** 主诊编码（兼容旧链路；与 {@link #clinicDiagnoses} 中主诊行一致）。 */
    private String mainDiagCode;
    /** 主诊名称（同时作 2203 mdtrtinfo.main_cond_dscr）。 */
    private String mainDiagName;

    /**
     * 本诊次全部有效临床诊断（Wave B：2203 diseinfo 多行来源）。
     * 挂号费无临床诊断时可为单行默认码；诊疗结算须非空且主诊优先。
     */
    private List<OpClinicDiagnosisLine> clinicDiagnoses;

    /** 本批次待结算收费 id（调用方传入，enrich 后转 chargeLines） */
    private List<Long> chargeItemIds;
    private List<OpSettleChargeLine> chargeLines;
    private BigDecimal medfeeSumamt;

    /** 院内收费合计（charge_item.total_price，结算两位）。 */
    private BigDecimal retailMedfeeSumamt;

    /** 2204 上传明细合计（pric×cnt，结算两位）。 */
    private BigDecimal uploadMedfeeSumamt;

    /** 零售−上传分差（retail−upload）。 */
    private BigDecimal retailUploadDiffAmt;

    private Long paymentDraftId;
    /** 本批次收费项挂载的结算账户（混合结算按组区分） */
    private Long accountId;
    private Long operatorId;
    private Long tenantId;

    private String acctUsedFlag;
    private String psnSetlway;

    /** 2207 发票号（可选） */
    private String invono;
    /** 2207 最小包装药品追溯码数量 */
    private Integer minpacuntDrugTracCnt;
    /** 2207 耗材追溯码数量 */
    private Integer mcsTracCnt;

    /** 混合预结算组序号（仅 preSettleBatch 结果回传） */
    private Integer groupIndex;
    /** 混合预结算组病种编码（仅 preSettleBatch 结果回传） */
    private String groupDiseCodg;
}
