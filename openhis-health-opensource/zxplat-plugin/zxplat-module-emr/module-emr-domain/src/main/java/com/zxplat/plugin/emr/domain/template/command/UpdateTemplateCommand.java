/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.template.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 更新病历模板命令（DM-EMR-002）；未传字段保持原值。
 */
@Getter
@Setter
public class UpdateTemplateCommand {

    private Long id;

    private String name;

    private String code;

    private String documentTypeCode;

    private String scopeLevel;

    private Long organizationId;

    /** 为 true 时用 organizationId（可为 null）覆盖原值。 */
    private boolean organizationIdSpecified;

    private Long ownerPractitionerId;

    /** 为 true 时用 ownerPractitionerId（可为 null）覆盖原值。 */
    private boolean ownerPractitionerIdSpecified;

    private String encounterClassCode;

    private Integer encounterTypeEnum;

    private String content;

    private Boolean enabled;
}
