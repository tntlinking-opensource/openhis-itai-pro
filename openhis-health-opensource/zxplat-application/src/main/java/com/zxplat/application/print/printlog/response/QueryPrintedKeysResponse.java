/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.print.printlog.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按业务主键批量回查「是否已打印」响应（PRT-GLOBAL-001 §6）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "按业务主键回查已打印响应")
public class QueryPrintedKeysResponse {

    /** 至少有一条 SUCCESS 命中的主键。 */
    @Schema(description = "已打印主键列表")
    private List<String> printedKeys = new ArrayList<>();

    /** 各主键最近一次 SUCCESS 打印时刻（yyyy-MM-dd HH:mm:ss）。 */
    @Schema(description = "主键 → 最近打印时刻")
    private Map<String, String> lastPrintedAtByKey = new LinkedHashMap<>();
}
