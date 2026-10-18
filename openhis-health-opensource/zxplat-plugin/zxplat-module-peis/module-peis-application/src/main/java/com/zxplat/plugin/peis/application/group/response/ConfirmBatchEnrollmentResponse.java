/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.response;

import com.zxplat.plugin.peis.application.group.dto.EnrollmentFailureRowDto;
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
