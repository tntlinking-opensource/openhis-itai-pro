/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.common.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** feedetail 单行（2204/2301 共用，未用字段不传） */
@Data
@Accessors(chain = true)
public class ChsFeeDetailLine {

    @JSONField(name = "feedetl_sn")
    private String feedetlSn;

    /** 原费用流水号；退单（cnt 为负）时必填，对应被退单的 {@code feedetl_sn}。 */
    @JSONField(name = "init_feedetl_sn")
    private String initFeedetlSn;

    @JSONField(name = "mdtrt_id")
    private String mdtrtId;

    @JSONField(name = "psn_no")
    private String psnNo;

    @JSONField(name = "chrg_bchno")
    private String chrgBchno;

    @JSONField(name = "dise_codg")
    private String diseCodg;

    @JSONField(name = "rxno")
    private String rxno;

    @JSONField(name = "rx_circ_flag")
    private String rxCircFlag;

    @JSONField(name = "med_type")
    private String medType;

    @JSONField(name = "fee_ocur_time")
    private String feeOcurTime;

    @JSONField(name = "med_list_codg")
    private String medListCodg;

    @JSONField(name = "medins_list_codg")
    private String medinsListCodg;

    @JSONField(name = "det_item_fee_sumamt")
    private BigDecimal detItemFeeSumamt;

    private BigDecimal cnt;

    private BigDecimal pric;

    @JSONField(name = "bilg_dept_codg")
    private String bilgDeptCodg;

    @JSONField(name = "bilg_dept_name")
    private String bilgDeptName;

    @JSONField(name = "bilg_dr_codg")
    private String bilgDrCodg;

    @JSONField(name = "bilg_dr_name")
    private String bilgDrName;

    @JSONField(name = "hosp_appr_flag")
    private String hospApprFlag;

    @JSONField(name = "dscg_tkdrug_flag")
    private String dscgTkdrugFlag;

    @JSONField(name = "drord_no")
    private String drordNo;
}
