/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.integrationlog.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * PACS 集成日志读模型（列表与详情共用；列表 Mapper 省略 body）。
 */
@Getter
@Setter
public class PacsIntegrationLogQueryModel {

    private Long id;

    private String directionEnum;

    private String actionCode;

    private String entityType;

    private Long entityId;

    private String outcomeEnum;

    private String requestBody;

    private String responseBody;

    private String errorMessage;

    private LocalDateTime createdAt;
}
