/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.servicerequest.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 住院反结软删出院医嘱命令（AS-IP-FIN-004 **BR-IP-RVS-007**）。
 *
 * <p>主档与明细逻辑删除；状态码不迁 {@code CANCELLED}，医生站列表不再展示。</p>
 */
@Getter
@Setter
public class SoftDeleteDischargeServiceRequestForReverseSettleCommand {

    /** 出院类服务申请主键。 */
    private Long serviceRequestId;

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 审计原因（写入 {@code reason_text}，如「住院反结」）；可空。
     */
    private String auditReason;
}
