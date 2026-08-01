/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.activityobservationlink.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 单条 AD↔OD 绑定项命令（saveLinks 子集）。
 */
@Getter
@Setter
public class ActivityObservationLinkItemCommand {

    /** 更新时传已有链接主键；新建为空。 */
    private Long id;

    /** 小项 ObservationDefinition 主键。 */
    private Long observationDefinitionId;

    /** 排序号。 */
    private Integer sortNum;

    /** 是否必检；空则默认 true。 */
    private Boolean requiredFlag;
}
