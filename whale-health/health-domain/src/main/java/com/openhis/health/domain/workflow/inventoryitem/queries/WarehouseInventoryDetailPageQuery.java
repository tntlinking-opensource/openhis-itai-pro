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

package com.openhis.health.domain.workflow.inventoryitem.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 药库库存明细（现库）分页/汇总/导出共用筛选条件。
 *
 * <p>机构收窄不通过本入参传递，由应用层在 Gate 阶段解析库房范围后写入 {@link #effectiveWarehouseIds}（B.10.11）。</p>
 */
@Getter
@Setter
public class WarehouseInventoryDetailPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 应用层门控后的库房主键范围（与请求 {@code warehouseIds} 及机构可见库房求交结果）；
     * 空列表表示不按库房主键收窄（须与产品安全策略一致，通常仅在会话缺省机构时出现）。
     */
    private List<Long> effectiveWarehouseIds;

    /** 供应商主键多选，语义为 SQL IN。 */
    private List<Long> supplierIds;

    /**
     * 药品类型编码多选；与 {@code hisrm_product_definition.category_code} 对齐，
     * 且谓词侧要求 {@code item_table} 为药品定义表。
     */
    private List<String> medicationCategoryCodes;

    /**
     * 耗材类型编码多选；与产品定义读模型 {@code category_code} 对齐，
     * 且谓词侧要求 {@code item_table} 为耗材定义表。
     */
    private List<String> deviceCategoryCodes;

    /** 医保等级多选，与药品/耗材主表 {@code chrgitm_lv} 子查询对齐。 */
    private List<Integer> chrgitmLvList;

    /** 供应状态持久化码多选，取值域为 {@code PublicationStatus#getCode()}。 */
    private List<String> inventoryStatusEnums;

    /**
     * 零库存筛选：{@code true} 仅账面为零；{@code false} 仅正库存；{@code null} 不按数量符号收窄。
     */
    private Boolean zeroStockOnly;

    /**
     * 近效期窗口天数：效期时刻不晚于「今天 + 天数」当日结束时刻的行；{@code null} 表示不按近效期收窄。
     */
    private Integer expireWithinDays;

    /**
     * 应用层解析后的药品定义主键（{@code item_table=med_medication_definition}）；由 {@code InventoryCatalogFilterSupport} 写入。
     */
    private List<Long> resolvedMedicationDefinitionIds;

    /**
     * 应用层解析后的耗材定义主键（{@code item_table=adm_device_definition}）；由 {@code InventoryCatalogFilterSupport} 写入。
     */
    private List<Long> resolvedDeviceDefinitionIds;
}
