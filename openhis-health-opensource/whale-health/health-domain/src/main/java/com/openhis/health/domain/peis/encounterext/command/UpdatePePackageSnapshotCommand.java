/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.encounterext.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 更新体检套餐快照命令。
 */
@Getter
@Setter
public class UpdatePePackageSnapshotCommand {

    /** 就诊主键。 */
    private Long encounterId;

    /** 套餐 ActivityDefinition 主键。 */
    private Long packageActivityId;

    /** 套餐快照 JSON。 */
    private String packageSnapshotJson;
}
