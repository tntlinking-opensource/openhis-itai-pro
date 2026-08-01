/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.audit.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** 3101 fsi_encounter_dtos 元素 */
@Data
@Accessors(chain = true)
public class Audit3101EncounterRow {

    @JSONField(name = "mdtrt_id")
    private String mdtrtId;

    @JSONField(name = "medins_id")
    private String medinsId;

    @JSONField(name = "medins_name")
    private String medinsName;

    @JSONField(name = "medins_admdvs")
    private String medinsAdmdvs;

    @JSONField(name = "medins_type")
    private String medinsType;

    @JSONField(name = "medins_lv")
    private String medinsLv;

    @JSONField(name = "wardarea_codg")
    private String wardareaCodg;

    private String wardno;

    private String bedno;

    @JSONField(name = "adm_date")
    private String admDate;

    @JSONField(name = "dscg_date")
    private String dscgDate;

    @JSONField(name = "dscg_main_dise_codg")
    private String dscgMainDiseCodg;

    @JSONField(name = "dscg_main_dise_name")
    private String dscgMainDiseName;

    @JSONField(name = "fsi_diagnose_dtos")
    private List<Audit3101DiagnoseRow> fsiDiagnoseDtos;

    @JSONField(name = "dr_codg")
    private String drCodg;

    @JSONField(name = "adm_dept_codg")
    private String admDeptCodg;

    @JSONField(name = "adm_dept_name")
    private String admDeptName;

    @JSONField(name = "dscg_dept_codg")
    private String dscgDeptCodg;

    @JSONField(name = "dscg_dept_name")
    private String dscgDeptName;

    @JSONField(name = "med_mdtrt_type")
    private String medMdtrtType;

    @JSONField(name = "med_type")
    private String medType;

    @JSONField(name = "fsi_order_dtos")
    private List<Audit3101OrderRow> fsiOrderDtos;

    /** 手术集合：优先发 fsi_operation_dtos（C2） */
    @JSONField(name = "fsi_operation_dtos")
    private List<Audit3101OperationRow> fsiOperationDtos;

    @JSONField(name = "matn_stas")
    private String matnStas;

    @JSONField(name = "medfee_sumamt")
    private BigDecimal medfeeSumamt;

    @JSONField(name = "ownpay_amt")
    private BigDecimal ownpayAmt;

    @JSONField(name = "selfpay_amt")
    private BigDecimal selfpayAmt;

    @JSONField(name = "acct_payamt")
    private BigDecimal acctPayamt;

    @JSONField(name = "ma_amt")
    private BigDecimal maAmt;

    @JSONField(name = "hifp_payamt")
    private BigDecimal hifpPayamt;

    @JSONField(name = "setl_totlnum")
    private Integer setlTotlnum;

    private String insutype;

    @JSONField(name = "reim_flag")
    private String reimFlag;

    @JSONField(name = "out_setl_flag")
    private String outSetlFlag;
}
