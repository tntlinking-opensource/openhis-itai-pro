/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.orderexecute.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量执行响应（IF-IP-EXE-02）。
 *
 * <p>{@code processedCount} 为本次同事务内成功完成的槽位数；任一条失败则整批回滚，计数为 0。</p>
 * <p>{@code warnings} 为软提示（如长嘱皮试药跨批，BR-IP-EXE-034）；不拒绝执行、不改状态；无提示时为空列表。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量执行响应")
public class ExecuteNurseStationOrdersResponse {

    @Schema(description = "成功处理的槽位数")
    /** 成功处理的槽位数。 */
    private Integer processedCount;

    /**
     * 软提示文案列表（BR-IP-EXE-034 等）；无提示时为空列表，永不因本字段拒绝执行。
     */
    @Schema(description = "软提示文案；无则空列表")
    private List<String> warnings;
}
