/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 挂号列表分页请求。分页与关键字见 {@link AbstractPageQuery}（B.10.11）；日期区间为挂号业务日（LocalDate）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "挂号列表分页请求")
public class PageClinicRegistersRequest extends AbstractHealthPageRequest {

    /** 开始日期（业务字段）。 */
    @Schema(description = "开始日期")
    private LocalDate dateFrom;

    /** 查询结束日期。 */
    @Schema(description = "结束日期")
    private LocalDate dateTo;

    /** 患者主键（按人查历史挂号）。 */
    @Schema(description = "患者主键（按人查历史挂号）")
    private Long patientId;
}
