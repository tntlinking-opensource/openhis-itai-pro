/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.reporttemplate.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存检查报告模板命令。
 */
@Getter
@Setter
public class SavePacsReportTemplateCommand {

    private Long id;

    private String name;

    private String modalityCode;

    private String findings;

    private String conclusion;

    private Boolean enabledFlag;

    private Integer sortOrder;

    private Long ownerPractitionerId;

    private Long tenantId;

    private Long operatorId;
}
