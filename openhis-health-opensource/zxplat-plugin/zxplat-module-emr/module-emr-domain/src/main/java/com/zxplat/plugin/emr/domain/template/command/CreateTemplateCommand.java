/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.template.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 新建病历模板命令（DM-EMR-002）。
 */
@Getter
@Setter
public class CreateTemplateCommand {

    private String name;

    private String code;

    private String documentTypeCode;

    private String scopeLevel;

    private Long organizationId;

    private Long ownerPractitionerId;

    private String encounterClassCode;

    private Integer encounterTypeEnum;

    private String content;

    private Boolean enabled;
}
