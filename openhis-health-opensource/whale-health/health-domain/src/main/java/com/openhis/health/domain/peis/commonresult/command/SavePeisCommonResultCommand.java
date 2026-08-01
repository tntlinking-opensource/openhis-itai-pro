/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.commonresult.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存 PEIS 常见结果命令。
 */
@Getter
@Setter
public class SavePeisCommonResultCommand {

    /** 更新时主键。 */
    private Long id;

    /** 租户标识。 */
    private Long tenantId;

    /** 院区标识。 */
    private Long orgId;

    /** 绑定小项。 */
    private Long observationDefinitionId;

    /** 科室机构。 */
    private Long deptOrgId;

    /** 模板名称。 */
    private String display;

    /** 默认结果值。 */
    private String valueString;

    /** 解读码。 */
    private String interpretationCode;

    /** 排序号。 */
    private Integer sortNum;

    /** 状态；空则默认 active。 */
    private String statusEnum;
}
