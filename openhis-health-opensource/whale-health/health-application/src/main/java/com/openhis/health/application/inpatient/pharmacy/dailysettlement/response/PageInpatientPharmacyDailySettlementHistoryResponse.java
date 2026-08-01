/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dailysettlement.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 住院药房交账历史分页响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院药房交账历史分页响应")
public class PageInpatientPharmacyDailySettlementHistoryResponse {

    /** 历史行。 */
    private List<InpatientPharmacyDailySettlementHistoryItemDto> items = new ArrayList<>();

    /** 总条数。 */
    private Long total;

    /** 页码。 */
    private Integer pageIndex;

    /** 页大小。 */
    private Integer pageSize;
}
