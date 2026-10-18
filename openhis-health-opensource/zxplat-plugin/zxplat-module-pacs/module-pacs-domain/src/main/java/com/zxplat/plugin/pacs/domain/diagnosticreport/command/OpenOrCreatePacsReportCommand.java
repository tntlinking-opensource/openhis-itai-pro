/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.diagnosticreport.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 打开或创建检查报告草稿。
 */
@Getter
@Setter
public class OpenOrCreatePacsReportCommand {

    private Long workItemId;

    private Long operatorId;
}
