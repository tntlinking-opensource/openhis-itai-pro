/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inventory.warehouse.inventorydetail.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 备份库存明细分页入参（IF-INV-DTL-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "备份库存明细分页 IF-INV-DTL-04")
public class PageInventoryDetailBackupRequest extends AbstractHealthPageRequest {

    /** 备份批次单号；未传时默认当自然日最新 ABU 批次（AS BR-INV-DTL-022）。 */
    @Schema(description = "备份批次单号（backupBusNo）；未传默认当天最新批次")
    private String backupBusNo;

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

    /** {@code true}：数量等于 0；{@code false}：数量大于 0；{@code null}：不限。 */
    @Schema(description = "库存范围：true 数量等于0，false 数量大于0，null 不限")
    private Boolean zeroStockOnly;

    /** 近效期窗口天数。 */
    @Schema(description = "近效期窗口天数，空表示不按近效期筛选")
    private Integer expireWithinDays;

    /** 供应状态持久化码多选。 */
    @Schema(description = "供应状态码多选（PublicationStatus#getCode）")
    private List<String> inventoryStatusEnums;
}
