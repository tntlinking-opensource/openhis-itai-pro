/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 首次批初始化：3501 盘存 + 3502 变更 */
@Data
@Accessors(chain = true)
public class InitializeInventoryCommand {

    private String contractBusNo;
    private Long contractId;
    /** 库房/药房位置 ID（必填） */
    private Long locationId;
    private String remark;
    /** 单次批处理上限（默认 200） */
    private Integer maxLines;
    /** 库存分页页码 起，续跑时传上次返回nextPageNo） */
    private Integer pageNo;
    /** 是否跳过已成功初始化的库存行（默true） */
    private Boolean skipInitialized;
    private List<InitializeInventoryPairCommand> pairs = new ArrayList<>();
}
