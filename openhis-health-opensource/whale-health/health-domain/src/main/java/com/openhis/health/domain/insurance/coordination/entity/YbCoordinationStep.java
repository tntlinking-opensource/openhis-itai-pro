/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.insurance.coordination.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 医保协同单步骤行（{@code yb_coordination_step}，多 infno 链）。
 *
 * <p>记录协同单内每一次 infno 调用的请求/回执快照与步骤状态，按 {@link #stepOrder} 升序回放。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "yb_coordination_step")
public class YbCoordinationStep extends BaseAuditedEntity {

    /** 所属协同单主键。 */
    @Column(name = "coordination_id", nullable = false)
    private Long coordinationId;

    /** 步骤序号，从 1 递增，决定 infno 调用顺序。 */
    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    /** 本步骤调用的 infno 交易号。 */
    @Column(name = "infno", nullable = false, length = 16)
    private String infno;

    /** 步骤状态（通常对齐协同单子状态或平台返回码映射）。 */
    @Column(name = "state", nullable = false, length = 32)
    private String state;

    /** 本步骤平台报文 msgid。 */
    @Column(name = "msgid", nullable = false, length = 64)
    private String msgid;

    /** 本步骤请求 JSON 快照。 */
    @Column(name = "request_snapshot", columnDefinition = "TEXT")
    private String requestSnapshot;

    /** 本步骤回执 JSON 快照。 */
    @Column(name = "response_snapshot", columnDefinition = "TEXT")
    private String responseSnapshot;

    /** 本步骤错误码。 */
    @Column(name = "error_code", length = 64)
    private String errorCode;

    /** 本步骤错误描述。 */
    @Column(name = "error_msg", length = 512)
    private String errorMsg;
}
