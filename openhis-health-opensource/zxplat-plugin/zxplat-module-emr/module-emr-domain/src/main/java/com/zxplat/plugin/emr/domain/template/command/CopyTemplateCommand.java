/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.template.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 复制病历模板命令（DM-EMR-002）。
 */
@Getter
@Setter
public class CopyTemplateCommand {

    private Long sourceTemplateId;

    private String newName;

    private String newCode;
}
