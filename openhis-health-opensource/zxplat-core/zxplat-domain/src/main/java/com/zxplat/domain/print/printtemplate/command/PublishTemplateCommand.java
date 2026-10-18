/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.print.printtemplate.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 发布画布打印模板命令（TC-PRT-TPL-PUB-001）。
 */
@Getter
@Setter
public class PublishTemplateCommand {

    /** 待发布 draft 主键。 */
    private Long draftId;

    /** 租户主键（可选校验）。 */
    private Long tenantId;

    /** 模板 busNo（可选校验）。 */
    private String busNo;
}
