/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.workitem.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 模态能力配置（{@code pacs_modality_capability}）。
 *
 * <p>校验 ActivityDefinition 是否具备可执行模态（BR-PWI-03）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "pacs_modality_capability")
public class PacsModalityCapability extends BaseAuditedEntity {

    /** ActivityDefinition 主键。 */
    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    /** 模态码。 */
    @Column(name = "modality_code", nullable = false)
    private String modalityCode;

    /** 是否启用。 */
    @Column(name = "enabled_flag", nullable = false)
    private Boolean enabledFlag = Boolean.TRUE;

    /** 可选科室范围。 */
    @Column(name = "organization_id")
    private Long organizationId;
}
