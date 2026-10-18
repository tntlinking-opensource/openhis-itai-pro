/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.prescriptionreview.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量创建点评响应（IF-PRV-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量创建点评响应")
public class BatchCreateReviewsResponse {

    /** 新建点评主键列表。 */
    @Schema(description = "新建点评主键列表")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> createdIds;

    /** 成功条数。 */
    @Schema(description = "成功条数")
    private Integer createdCount;
}
