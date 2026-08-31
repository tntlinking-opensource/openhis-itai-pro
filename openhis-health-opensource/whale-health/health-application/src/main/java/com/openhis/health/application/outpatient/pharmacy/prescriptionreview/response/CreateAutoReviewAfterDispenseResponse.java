/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionreview.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 发药完成后自动点评响应（IF-PRV-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "发药完成后自动点评响应")
public class CreateAutoReviewAfterDispenseResponse {

    /** 新建点评主键列表。 */
    @Schema(description = "新建点评主键列表")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> createdIds;

    /** 新建条数。 */
    @Schema(description = "新建条数")
    private Integer createdCount;

    /** 因已有有效点评而跳过的条数。 */
    @Schema(description = "跳过条数")
    private Integer skippedCount;

    /**
     * 点评侧软失败：true 表示自动点评未完整写入。
     * <p>成功路径为 false；失败路径由 IF-PRV-06 <strong>自行置 true 并正常返回</strong>，
     * 禁止再抛异常，以免与发药同事务时连带回滚已成功发药（BR-PRV-A11）。</p>
     */
    @Schema(description = "点评软失败（不影响发药成功）")
    private Boolean softFailed;
}
