/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.specimentubemerge.command;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 按设备全量对齐合管绑定行命令。
 */
@Getter
@Setter
public class AlignSpecimenTubeMergeCommand {

    /** 设备主键（adm_device.id）。 */
    private Long deviceId;

    /** 操作人（软删审计）。 */
    private Long operatorId;

    /** 全量目标行；空列表表示清空该设备全部绑定。 */
    private List<AlignSpecimenTubeMergeLineCommand> lines = new ArrayList<>();
}
