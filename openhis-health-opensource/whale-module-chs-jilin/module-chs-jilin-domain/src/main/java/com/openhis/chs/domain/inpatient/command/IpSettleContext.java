/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 住院医保结算编排上下文 */
@Data
@Accessors(chain = true)
public class IpSettleContext {

    public static final String SETTLE_MODE_DISCHARGE_FULL = "DISCHARGE_FULL";
    public static final String SETTLE_MODE_MID = "MID_SETTLE";

    private Long contractId;
    private String contractBusNo;
    private Long encounterId;
    private String encounterBusNo;
    private Long patientId;
    private LocalDateTime admissionTime;
    private LocalDateTime dischargeTime;
    private String settleMode;

    private String admDeptCodg;
    private String admDeptName;
    private String dscgDeptCodg;
    private String dscgDeptName;
    private String admBed;
    private String dscgBed;
    private String dscgWay;

    private String atddrNo;
    private String chfpdrName;
    private String mainDiagCode;
    private String mainDiagName;
    private String admDiagDscr;

    private String mdtrtCertType;
    private String mdtrtCertNo;
    private String psnNo;
    private String insuplcAdmdvs;
    private String insutype;
    private String medType;
    private String mdtrtareaAdmvs;

    private List<IpSettleChargeLine> chargeLines;
    private BigDecimal medfeeSumamt;

    private Long paymentDraftId;
    private Long operatorId;
    private Long tenantId;

    private String acctUsedFlag;
    private String psnSetlway;

    /** 2304 发票号（可选） */
    private String invono;
    /** 2304 最小包装药品追溯码数量 */
    private Integer minpacuntDrugTracCnt;
    /** 2304 耗材追溯码数量 */
    private Integer mcsTracCnt;
}
