/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.periodbalancedetail.response;

import com.zxplat.application.inventory.periodbalancedetail.dto.PeriodBalanceDetailColumnDto;
import com.zxplat.application.inventory.periodbalancedetail.dto.PeriodBalanceDetailListRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 库房期初期末明细分页出参（IF-INV-SET-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库房期初期末明细分页 IF-INV-SET-02")
public class PagePeriodBalanceDetailResponse {

    /** 过滤后总行数。 */
    @Schema(description = "过滤后总行数")
    private Long total;

    /** 当前页码。 */
    @Schema(description = "当前页码")
    private Integer pageNo;

    /** 页大小。 */
    @Schema(description = "页大小")
    private Integer pageSize;

    /** 当前库位 LocationForm code。 */
    @Schema(description = "当前库位 LocationForm code")
    private String locationFormEnum;

    /** 服务端解析后的物品主表（med_medication_definition / adm_device_definition）。 */
    @Schema(description = "服务端解析后的物品主表")
    private String itemTable;

    /** 当前形态适用动态列 code 列表。 */
    @Schema(description = "当前形态适用动态列 code 列表")
    private List<String> activeColumnCodes;

    /** 列元数据。 */
    @Schema(description = "列元数据")
    private List<PeriodBalanceDetailColumnDto> columns;

    /** 当前页数据行。 */
    @Schema(description = "当前页数据行")
    private List<PeriodBalanceDetailListRowDto> rows;
}
