/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.invoke.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * prepareInvoke 请求（POST /api/app/workInjuryInvoke/prepareInvoke）。
 */
@Data
@Accessors(chain = true)
public class PrepareWorkInjuryInvokeRequest {

    /** 首次 null，后续携带服务端下发的 sessionId。 */
    private String sessionId;

    /** REG_QUERY | REG_APPROVAL | OP_SETTLE | OP_REVERSE | RECON。 */
    private String phase;

    /** 就诊 id。 */
    private Long encounterId;

    /** 费用性质 id。 */
    private Long contractId;

    /** OP_SETTLE 时必填。 */
    private Long paymentDraftId;

    /** 操作员 id（可选，服务端可解析当前用户）。 */
    private Long operatorId;

    /** 2301 表单 / chargeItemIds 等扩展上下文。 */
    private Map<String, Object> context;
}
