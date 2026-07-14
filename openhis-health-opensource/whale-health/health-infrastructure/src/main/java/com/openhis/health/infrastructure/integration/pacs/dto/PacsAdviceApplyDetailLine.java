/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.pacs.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 检查外发明细行。
 */
@Getter
@Setter
public class PacsAdviceApplyDetailLine {

    /** 目标业务表名。 */
    private String targetTable;

    /** 目标行主键。 */
    private Long targetId;

    /** 目标展示名（检查项目名称）。 */
    private String targetDisplayName;

    /** 备注。 */
    private String remark;
}
