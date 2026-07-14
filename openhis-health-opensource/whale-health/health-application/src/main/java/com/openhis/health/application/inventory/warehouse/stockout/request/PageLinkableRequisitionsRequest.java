/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.stockout.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 可关联请领单分页请求（IF-WHS-SO-12）。
 *
 * <p>继承 {@link AbstractHealthPageRequest}（B.10.11）；默认含药房请领与科室请领，可由 {@link #requisitionTypeEnum} 收窄。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "可关联请领单分页 IF-WHS-SO-12")
public class PageLinkableRequisitionsRequest extends AbstractHealthPageRequest {

    @JsonSerialize(using = ToStringSerializer.class)
    /** 本药库位置 sourceLocationId；0 表示全部药库。 */
    @Schema(description = "本药库位置 sourceLocationId；0 表示全部药库", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceLocationId;

    /**
     * 可选请领类型收窄：{@code PHARMACY_REQUISITION} 或 {@code DEPT_REQUISITION}；空则两类并集。
     */
    @Schema(description = "请领类型收窄，空则药房+科室请领")
    private String requisitionTypeEnum;
}
