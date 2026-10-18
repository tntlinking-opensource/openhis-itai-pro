/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.integrationlog.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * PACS 集成接口日志（对标遗留 WBS_LOG · {@code pacs_integration_log}）。
 */
@Getter
@Setter
@Entity
@Table(name = "pacs_integration_log")
public class PacsIntegrationLog extends BaseAuditedEntity {

    /** 方向：outbound / inbound。 */
    @Column(name = "direction_enum", nullable = false, length = 16)
    private String directionEnum;

    /** 动作码（如 sendApply / reportFinalize / retry）。 */
    @Column(name = "action_code", nullable = false, length = 64)
    private String actionCode;

    /** 关联业务类型（如 PacsWorkItem）。 */
    @Column(name = "entity_type", length = 64)
    private String entityType;

    /** 关联业务主键。 */
    @Column(name = "entity_id")
    private Long entityId;

    /** 结果：success / failure。 */
    @Column(name = "outcome_enum", nullable = false, length = 16)
    private String outcomeEnum;

    /** 请求摘要或 Mock 报文（PostgreSQL text，禁止 @Lob，否则校验期望 oid）。 */
    @Column(name = "request_body", columnDefinition = "text")
    private String requestBody;

    /** 响应摘要或 Mock 报文（PostgreSQL text，禁止 @Lob，否则校验期望 oid）。 */
    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    /** 失败原因（可空）。 */
    @Column(name = "error_message", length = 512)
    private String errorMessage;
}
