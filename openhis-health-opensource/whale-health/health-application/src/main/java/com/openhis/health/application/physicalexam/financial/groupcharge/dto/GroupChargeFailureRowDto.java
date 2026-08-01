/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.groupcharge.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检批量结算失败行 DTO。
 */
@Getter
@Setter
public class GroupChargeFailureRowDto {

    private Long rosterId;
    private String message;
}
