/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** 挂号待遇预览请求。 */
@Data
@Accessors(chain = true)
@Schema(description = "挂号待遇预览请求")
public class LoadRegisterBenefitPreviewRequest {

    /** 患者ID。 */
    @Schema(description = "患者ID")
    private Long patientId;

    /** 费用性质ID。 */
    @Schema(description = "费用性质ID")
    private Long contractId;

    /** 就诊凭证类型（医保读卡）。 */
    @Schema(description = "就诊凭证类型（医保读卡）")
    private String ybMediumType;

    /** 就诊凭证编号（医保读卡，社会保障卡卡号）。 */
    @Schema(description = "就诊凭证编号（医保读卡，社会保障卡卡号）")
    private String ybCardInfo;

    /** 卡识别码（1101 card_sn，社保卡读卡）。 */
    @Schema(description = "卡识别码（1101 card_sn，社保卡读卡）")
    private String ybCardSn;

    /** 证件号码（1101 certno，社保卡/电子凭证读卡）。 */
    @Schema(description = "证件号码（1101 certno，社保卡/电子凭证读卡）")
    private String ybCertno;

    /** 人员姓名（1101 psn_name，电子凭证读卡）。 */
    @Schema(description = "人员姓名（1101 psn_name，电子凭证读卡）")
    private String ybPsnName;

    /** 参保地医保区划。 */
    @Schema(description = "参保地医保区划")
    private String insuplcAdmdvs;

    /** 学生记录ID（公费分支）。 */
    @Schema(description = "学生记录ID（公费分支）")
    private Long studentId;

    /** 人员编号（已有 1101 结果时可传，仅调 2001）。 */
    @Schema(description = "人员编号（已有 1101 结果时可传，仅调 2001）")
    private String psnNo;

    /** 险种类型。 */
    @Schema(description = "险种类型")
    private String insutype;

    /** 医疗类别，默认 11。 */
    @Schema(description = "医疗类别，默认 11")
    private String medType;

    /** 是否调用 2001 待遇检查，默认 true。 */
    @Schema(description = "是否调用 2001 待遇检查，默认 true")
    private Boolean runTreatmentCheck;

    /** 是否调用 5301 慢特病备案查询，默认 true。 */
    @Schema(description = "是否调用 5301 慢特病备案查询，默认 true")
    private Boolean runOpspFilingQuery;

    /** 是否调用 5206 人员累计查询，默认 true。 */
    @Schema(description = "是否调用 5206 人员累计查询，默认 true")
    private Boolean runCumulativeQuery;
}
