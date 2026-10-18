/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.application.shared.pagination.PageQueryBinder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 调价单列表分页请求（IF-PRC-02）。
 * <p>操作科室由服务端从当前登录会话解析，不在请求体上传。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "调价单列表分页请求")
public class PagePriceAdjustmentDocumentRequest extends PageQueryBinder.AbstractHealthPageRequest {

    /** 审批状态 code。 */
    @Schema(description = "审批状态 code")
    private String statusEnum;

    /** 提交时间下限。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "提交时间下限")
    private LocalDateTime submitTimeFrom;

    /** 提交时间上限。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "提交时间上限")
    private LocalDateTime submitTimeTo;
}
