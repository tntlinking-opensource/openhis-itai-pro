/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 现库存明细分页查询入参（IF-INV-DTL-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "现库存明细分页 IF-INV-DTL-02")
public class PageInventoryDetailCurrentRequest extends AbstractHealthPageRequest {

    /** 药品类型编码多选。 */

    @Schema(description = "药品类型编码多选")
    private List<String> medicationCategoryCodes;

    /** 耗材类型编码多选。 */
    @Schema(description = "耗材类型编码多选")
    private List<String> deviceCategoryCodes;

    /** 库房主键多选。 */
    @Schema(description = "库房主键多选")
    private List<Long> warehouseIds;

    /** 供应商主键多选。 */
    @Schema(description = "供应商主键多选")
    private List<Long> supplierIds;

    /** 医保等级多选。 */
    @Schema(description = "医保等级多选")
    private List<Integer> chrgitmLvList;

    /**
     * 基药标识筛选（药典 {@code basic_flag}）；仅收窄药品行；{@code null} 不限。
     */
    @Schema(description = "基药标识：true/false；null 不限（仅药品）")
    private Boolean basicFlag;

    /** 库存范围：true 数量等于0，false 数量大于0，null 不限。 */
    @Schema(description = "库存范围：true 数量等于0，false 数量大于0，null 不限")
    private Boolean zeroStockOnly;

    /** 近效期窗口天数，空表示不按近效期筛选。 */
    @Schema(description = "近效期窗口天数，空表示不按近效期筛选")
    private Integer expireWithinDays;

    /** 供应状态码多选（PublicationStatus#getCode）。 */
    @Schema(description = "供应状态码多选（PublicationStatus#getCode）")
    private List<String> inventoryStatusEnums;

    /** 预警模式：expire=效期预警，lowStock=库存预警。 */
    @Schema(description = "预警模式：expire=效期预警，lowStock=库存预警")
    private String warningMode;
}
