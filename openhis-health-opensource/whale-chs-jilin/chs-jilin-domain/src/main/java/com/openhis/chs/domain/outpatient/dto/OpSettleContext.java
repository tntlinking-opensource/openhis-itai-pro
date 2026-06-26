/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.outpatient.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 门诊医保结算编排上下文（由 adapter 从 HIS 加载）。 */
@Data
@Accessors(chain = true)
public class OpSettleContext {

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

    private List<OpSettleChargeLine> chargeLines;
    private BigDecimal medfeeSumamt;

    private Long paymentDraftId;
    /** 本批次收费项挂载的结算账户（混合结算按组区分）。 */
    private Long accountId;
    private Long operatorId;
    private Long tenantId;

    private String acctUsedFlag;
    private String psnSetlway;

    /** 2207 发票号（可选）。 */
    private String invono;
    /** 2207 最小包装药品追溯码数量。 */
    private Integer minpacuntDrugTracCnt;
    /** 2207 耗材追溯码数量。 */
    private Integer mcsTracCnt;
}
