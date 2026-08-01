/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.request;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊预结算请求。
 */
@Data
@Accessors(chain = true)
public class PreSettleOutpatientChargesRequest {

    /** 就诊主键。 */
    private Long encounterId;

    /** 本次拟预结算的收费明细主键列表。 */
    private List<Long> chargeItemIds;

    /** 费用性质合同主键（可选）。 */
    private Long contractId;

    /** 医保介质类型（读卡渠道口径）。 */
    private String ybMediumType;

    /** 医保读卡原始信息（JSON/密文，由前端传入）。 */
    private String ybCardInfo;

    /** 1101 人员编号（读卡后填入）。 */
    private String psnNo;

    /** 参保地医保区划（1101 返回）。 */
    private String insuplcAdmdvs;

    /** 险种类型（可选，默认取 1101/账户）。 */
    private String insutype;

    /** 医疗类别（可选，默认普通门诊 11）。 */
    private String medType;

    /**
     * 公费收费模式（Campus ADR-CMP-001 D5）。
     *
     * <p>{@code SELF_PAY_GF} / {@code AFTER_YB_GF}；空则不拆公费。
     * 真实改支付草稿见 {@code TODO(ADR-CMP-001 D5/D12)}，本字段先落契约供前端联调。</p>
     */
    private String gfChargeMode;
}
