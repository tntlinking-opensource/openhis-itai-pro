/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.masterreview.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待总检列表请求（IF-PEIS-MAS-01）。
 *
 * <p>业务日起止复用父类 {@code startDate}/{@code endDate}（{@code LocalDateTime}）；
 * Mapper 按自然日边界归一。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPendingMasterRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    /** 团检标志：{@code true} 仅团检；{@code false} 仅个检。 */
    private Boolean groupFlag;
}
