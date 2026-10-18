/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.settle.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * 门诊工伤预结算 invoke 启动命令（phase=OP_SETTLE，2201→2207 链入口）。
 */
@Data
@Accessors(chain = true)
public class StartWorkInjuryPreSettleCommand {

    /** 就诊 id。 */
    private Long encounterId;
    /** 工伤费用性质 contract id。 */
    private Long contractId;
    /** 收银草稿 id。 */
    private Long paymentDraftId;
    /** 操作员 id。 */
    private Long operatorId;
    /** 待结算收费项 id 列表（→ 2204 明细）。 */
    private List<Long> chargeItemIds;
    /** 扩展上下文（chargeItemIds 冗余、表单字段等）。 */
    private Map<String, Object> context;
}
