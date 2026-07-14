/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.tracenumber.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * IF-TRC-RPT-01：追溯码查询报表页初始化入参（空壳；租户 Gate）。
 */
@Getter
@Setter
@Schema(description = "追溯码查询报表初始化 IF-TRC-RPT-01")
public class LoadTraceNumberReportFormInitRequest {
}
