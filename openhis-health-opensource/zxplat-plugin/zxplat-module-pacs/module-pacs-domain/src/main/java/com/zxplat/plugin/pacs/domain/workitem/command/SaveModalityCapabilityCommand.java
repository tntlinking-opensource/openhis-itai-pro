/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.workitem.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存模态能力命令。
 */
@Getter
@Setter
public class SaveModalityCapabilityCommand {

    private Long id;

    private Long activityId;

    private String modalityCode;

    private Boolean enabledFlag;

    private Long organizationId;

    private Long tenantId;

    private Long operatorId;
}
