/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.dailysettlement.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 住院交账历史分页响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院交账历史分页响应")
public class PageInpatientCashierSettlementHistoryResponse {

    private List<InpatientCashierSettlementHistoryItemDto> items = new ArrayList<>();
    private Long total;
    private Integer pageIndex;
    private Integer pageSize;
}
