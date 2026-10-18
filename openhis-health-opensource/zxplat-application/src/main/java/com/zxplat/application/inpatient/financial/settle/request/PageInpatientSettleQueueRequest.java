/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.request;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 住院结算患者队列分页请求（IF-IP-STL-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "住院结算患者队列分页请求")
public class PageInpatientSettleQueueRequest extends AbstractHealthPageRequest {

    /** 入院日期筛选（业务字段）。 */
    @Schema(description = "入院日期筛选")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    private LocalDate admissionDate;

    /** 队列页签：PENDING_SETTLE / SETTLED。 */
    @Schema(description = "结算队列页签 code")
    private String settleQueueTab;
}
