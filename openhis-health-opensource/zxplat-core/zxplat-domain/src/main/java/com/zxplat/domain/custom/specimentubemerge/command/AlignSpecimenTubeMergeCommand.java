/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.specimentubemerge.command;

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
     * <p>可空：本设备各组均未勾选采血费时为 null。有值时该目标<strong>不</strong>做跨组/跨设备全局唯一校验
     * （BR-STM-002：仅检验项目全局唯一；采血费/耗材可跨组、跨设备复用）。</p>
     */
    private Long bloodCollectionFeeActivityId;

    /**
     * 全量目标行（可含多组）；空列表表示清空该设备全部绑定。
     *
     * <p>每行须带 {@code groupNo}；同设备内检验项目跨组不可重复。</p>
     */
    private List<AlignSpecimenTubeMergeLineCommand> lines = new ArrayList<>();
}
