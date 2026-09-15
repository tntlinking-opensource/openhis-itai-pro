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

    /**
     * 采血费 activity 主键（名称精确匹配「采血费」解析结果）。
     *
     * <p>可空：未勾选采血费时为 null。有值时该目标<strong>不</strong>做跨设备全局唯一校验
     * （BR-STM-002：仅检验项目全局唯一；采血费/耗材可跨设备复用）。</p>
     */
    private Long bloodCollectionFeeActivityId;

    /** 全量目标行；空列表表示清空该设备全部绑定。 */
    private List<AlignSpecimenTubeMergeLineCommand> lines = new ArrayList<>();
}
