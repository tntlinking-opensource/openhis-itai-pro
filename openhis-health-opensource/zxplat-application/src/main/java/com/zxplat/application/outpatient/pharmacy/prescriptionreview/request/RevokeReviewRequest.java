/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.prescriptionreview.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 撤销点评请求（IF-PRV-05；id 与 prescriptionNo 二选一，优先 id）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "撤销点评请求")
public class RevokeReviewRequest {

    /** 点评主键；与处方号二选一，优先本字段。 */
    @Schema(description = "点评主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 处方号锚点；id 为空时必填。 */
    @Schema(description = "处方号")
    private String prescriptionNo;
}
