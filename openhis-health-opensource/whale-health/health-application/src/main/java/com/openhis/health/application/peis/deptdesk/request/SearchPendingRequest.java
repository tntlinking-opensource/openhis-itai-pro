/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.deptdesk.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分科诊台待检列表请求（IF-PEIS-DES-01）。
 *
 * <p>业务日起止复用父类 {@code startDate}/{@code endDate}（{@code LocalDateTime}）；
 * Mapper 按自然日边界归一。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPendingRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    /** 科室机构主键（可选）。 */
    @Schema(description = "科室机构主键")
    private Long deptOrgId;
}
