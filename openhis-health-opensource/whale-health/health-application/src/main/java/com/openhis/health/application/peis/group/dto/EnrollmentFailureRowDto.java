/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.dto;

import lombok.Data;

/**
 * 团检建单失败行。
 */
@Data
public class EnrollmentFailureRowDto {

    private Long rosterId;

    private String fullName;

    private String message;
}
