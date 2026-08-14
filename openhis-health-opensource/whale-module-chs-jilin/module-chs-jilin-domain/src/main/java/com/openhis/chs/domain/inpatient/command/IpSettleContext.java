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
    /** 患者姓名；供 3101 {@code patient_dtos.patn_name}。 */
    private String patientName;
    /** HIS 行政性别码（MALE/FEMALE/UNKNOWN）；投影时转医保 GEND。 */
    private String patientGender;
    /**
     * 出生日期（yyyy-MM-dd），供 3101 {@code patient_dtos.brdy}；
     * 由 ContextLoader 从 Encounter 患者上下文写入（对齐门诊）。
     */
    private String brdy;
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

    /** 院内收费合计（charge_item.total_price，结算两位）。 */
    private BigDecimal retailMedfeeSumamt;

    /** 2301 上传明细合计（pric×cnt，结算两位）。 */
    private BigDecimal uploadMedfeeSumamt;

    /** 零售−上传分差（retail−upload）。 */
    private BigDecimal retailUploadDiffAmt;

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
