/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.request;

import lombok.Data;

import java.util.List;

/**
 * 团检批量免收费建单请求（P0-1）。
 */
@Data
public class ConfirmBatchEnrollmentRequest {

    /** 名册主键列表。 */
    private List<Long> rosterIds;

    /** 机构主键（可选，默认取批次/合同）。 */
    private Long orgId;
}
