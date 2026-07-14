/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.lis.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 检验外发明细行。
 */
@Getter
@Setter
public class LisAdviceApplyDetailLine {

    /** 目标业务表名（如 activity_definition 所在表逻辑名）。 */
    private String targetTable;

    /** 目标行主键。 */
    private Long targetId;

    /** 目标展示名（检验项目名称）。 */
    private String targetDisplayName;

    /** 备注（标本/执行说明等）。 */
    private String remark;
}
