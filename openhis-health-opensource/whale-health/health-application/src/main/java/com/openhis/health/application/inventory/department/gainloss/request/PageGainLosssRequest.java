/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.gainloss.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 损益单分页列表入参（IF-DEPT-GL-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "损益单分页列表 IF-DEPT-GL-02")
public class PageGainLosssRequest extends AbstractHealthPageRequest {

    /**
     * 损益科室药房；{@code 0} 表示当前用户可见全部药房
     * （见 {@link com.openhis.health.domain.share.constants.InventoryDepartmentConstants#ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID}）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "损益科室药房 purposeLocationId，0 表示全部可见药房")
    /** 损益科室药房 purposeLocationId，0 表示全部可见药房。 */
    private Long purposeLocationId;

    /** 状态 statusEnum。 */
    @Schema(description = "状态 statusEnum")
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
