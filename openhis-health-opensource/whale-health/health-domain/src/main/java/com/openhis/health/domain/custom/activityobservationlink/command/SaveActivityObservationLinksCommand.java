/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.activityobservationlink.command;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 全量同步某项目下的 AD↔OD 绑定集命令（CFG-04）。
 */
@Getter
@Setter
public class SaveActivityObservationLinksCommand {

    /** 项目 ActivityDefinition 主键。 */
    private Long activityDefinitionId;

    /** 租户标识。 */
    private Long tenantId;

    /** 机构标识。 */
    private Long orgId;

    /** 操作人（软删审计）。 */
    private Long operatorId;

    /** 全量目标链接集；空列表表示清空该项目全部绑定。 */
    private List<ActivityObservationLinkItemCommand> links = new ArrayList<>();
}
