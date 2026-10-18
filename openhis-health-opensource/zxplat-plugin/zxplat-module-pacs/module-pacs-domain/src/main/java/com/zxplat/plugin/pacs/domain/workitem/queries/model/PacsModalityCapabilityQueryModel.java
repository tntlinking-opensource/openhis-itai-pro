/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.workitem.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 模态能力读模型。
 */
@Getter
@Setter
public class PacsModalityCapabilityQueryModel {

    private Long id;

    private Long activityId;

    private String modalityCode;

    private Boolean enabledFlag;

    private Long organizationId;

    private LocalDateTime updatedAt;
}
