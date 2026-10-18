/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 工伤客户端 invoke 会话（{@code yb_wi_invoke_session}；prepare/submit 多步状态，见 11 §4.1）。
 */
@Getter
@Setter
@Entity
@Table(name = "yb_wi_invoke_session")
public class YbWiInvokeSession extends BaseAuditedEntity {

    /** UUID，前端 invoke 循环主键。 */
    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    /** 就诊 id。 */
    @Column(name = "encounter_id")
    private Long encounterId;

    /** 工伤费用性质 contract id。 */
    @Column(name = "contract_id")
    private Long contractId;

    /** OP_SETTLE 时关联收银草稿。 */
    @Column(name = "payment_draft_id")
    private Long paymentDraftId;

    /** {@link com.zxplat.plugin.wi.gateway.scheme.shared.model.WiPhase} 码。 */
    @Column(name = "phase", nullable = false, length = 16)
    private String phase;

    /** 0-based；submit 成功后递增。 */
    @Column(name = "step_index", nullable = false)
    private Integer stepIndex;

    /** prepare 下发、submit 须原样回传（防重放）。 */
    @Column(name = "pending_step_token", length = 36)
    private String pendingStepToken;

    /** ACTIVE / COMPLETED / FAILED。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;
}
