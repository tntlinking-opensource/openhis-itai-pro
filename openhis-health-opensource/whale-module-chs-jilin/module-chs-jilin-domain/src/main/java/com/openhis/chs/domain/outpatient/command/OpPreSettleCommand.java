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
    private String medType;

    private String mainDiagCode;
    private String mainDiagName;

    /** 本批次待结算收费 id（调用方传入，enrich 后转 chargeLines） */
    private List<Long> chargeItemIds;
    private List<OpSettleChargeLine> chargeLines;
    private BigDecimal medfeeSumamt;

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
