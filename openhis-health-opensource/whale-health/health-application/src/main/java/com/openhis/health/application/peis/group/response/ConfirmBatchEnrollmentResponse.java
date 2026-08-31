/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.response;

import com.openhis.health.application.peis.group.dto.EnrollmentFailureRowDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 团检批量免收费建单响应。
 */
@Data
public class ConfirmBatchEnrollmentResponse {

    private int successCount;

    private List<EnrollmentFailureRowDto> failures = new ArrayList<>();
}
