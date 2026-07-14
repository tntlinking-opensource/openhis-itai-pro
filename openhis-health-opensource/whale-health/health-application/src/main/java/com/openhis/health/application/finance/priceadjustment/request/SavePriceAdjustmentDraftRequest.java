/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.List;

/**
 * 保存调价草稿请求（IF-PRC-09）。
 * <p>操作科室（制单机构 {@code org_id}）由服务端从当前登录会话解析，不在请求体上传。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "保存调价草稿请求", requiredProperties = {"adjustmentReason", "lines"})
public class SavePriceAdjustmentDraftRequest {

    /** 调价单号。 */
    @Schema(description = "调价单号")
    private String busNo;

    /** 调价原因 code。 */
    @NotBlank(message = "调整原因不能为空")
    @Schema(description = "调价原因 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String adjustmentReason;

    /** 制单时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "制单时间")
    private Date adjustmentTime;

    /** 项目行列表。 */
    @NotEmpty(message = "调价项目行不能为空")
    @Schema(description = "项目行列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<SavePriceAdjustmentDraftLineRequest> lines;
}
