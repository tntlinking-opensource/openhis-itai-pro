/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** 刷新挂号费医保预结算（重新读码后 2205→2206 重算）。 */
@Data
@Accessors(chain = true)
@Schema(description = "刷新挂号费医保预结算请求")
public class RefreshRegisterFeeInsurancePreSettleRequest {

    /** 支付草稿 ID（须仍为 DRAFT 且就诊 PLANNED）。 */
    @Schema(description = "支付草稿ID")
    private Long paymentDraftId;

    /** 是否使用医保个人账户（acct_used_flag=1）；缺省时可从原快照复用。 */
    @Schema(description = "是否使用医保个人账户（acct_used_flag=1）")
    private Boolean ybUseAcctPay;

    /** 就诊凭证类型（重新读卡/展码后的 mdtrt_cert_type）。 */
    @Schema(description = "就诊凭证类型（重新读卡/展码）")
    private String ybMediumType;

    /** 就诊凭证编号（重新读卡/展码后的 mdtrt_cert_no，电子凭证为 ecToken）。 */
    @Schema(description = "就诊凭证编号（重新读卡/展码）")
    private String ybCardInfo;

    /** 人员编号 psn_no；缺省时可从原预结算快照复用。 */
    @Schema(description = "人员编号 psn_no")
    private String psnNo;

    /** 参保地医保区划；缺省时可从原预结算快照复用。 */
    @Schema(description = "参保地医保区划")
    private String insuplcAdmdvs;

    /** 险种类型；缺省时可从原预结算快照复用。 */
    @Schema(description = "险种类型")
    private String insutype;

    /** 医疗类别；缺省为普通门诊 11。 */
    @Schema(description = "医疗类别，默认 11")
    private String medType;
}
