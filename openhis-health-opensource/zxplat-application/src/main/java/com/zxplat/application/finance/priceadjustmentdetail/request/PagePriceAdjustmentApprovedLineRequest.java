/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustmentdetail.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 分页查询已生效调价项目主行请求（IF-PRC-DTL-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "分页查询调价明细请求")
public class PagePriceAdjustmentApprovedLineRequest extends AbstractHealthPageRequest {

    /** 生效时间下限。 */
    @NotNull(message = "生效时间下限不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "生效时间下限", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime approveTimeFrom;

    /** 生效时间上限。 */
    @NotNull(message = "生效时间上限不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "生效时间上限", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime approveTimeTo;

    /** 项目物理表 item_table。 */
    @Schema(description = "项目物理表 item_table")
    private String itemTable;

    /** 项目名称关键字。 */
    @Schema(description = "项目名称关键字")
    private String itemNameKeyword;

    /** 院内项目编码关键字。 */
    @Schema(description = "院内项目编码关键字")
    private String itemCodeKeyword;

    /** 调价单号。 */
    @Schema(description = "调价单号")
    private String busNo;
}
