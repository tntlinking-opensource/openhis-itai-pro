/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.gainloss.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 药库损益单分页列表请求（IF-WHS-GL-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药库损益单分页列表 IF-WHS-GL-02")
public class PageGainLosssRequest extends AbstractHealthPageRequest {

    /**
     * 损益药库；{@code 0} 表示当前用户可见全部药库
     * （见 {@link com.openhis.health.domain.share.constants.InventoryPharmacyConstants#ALL_PHARMACIES_LIST_FILTER_LOCATION_ID}）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "损益药库 purposeLocationId，0 表示全部可见药库")
    /** 损益药库 purposeLocationId，0 表示全部可见药库。 */
    private Long purposeLocationId;

    /** 单据状态 SupplyStatus#getCode。 */
    @Schema(description = "单据状态 SupplyStatus#getCode")
    private String statusEnum;

    /** 申请时间起。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "申请时间起")
    private LocalDateTime applyTimeFrom;

    /** 申请时间止。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "申请时间止")
    private LocalDateTime applyTimeTo;
}
