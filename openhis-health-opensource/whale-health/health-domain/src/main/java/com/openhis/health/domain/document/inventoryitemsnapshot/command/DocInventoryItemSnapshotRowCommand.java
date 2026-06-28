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

package com.openhis.health.domain.document.inventoryitemsnapshot.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单条库存明细快照写入行命令（DM-DOC-001 SDD §5.1.1）。
 *
 * <p>不含批次级 {@code busNo}/{@code snapshotTime}；由 {@link BatchAppendDocInventoryItemSnapshotCommand} 统一携带。</p>
 */
@Getter
@Setter
public class DocInventoryItemSnapshotRowCommand {

    /** 源库存项主键（写入时刻 {@code wkf_inventory_item.id}）。 */
    private Long inventoryItemId;

    /** 物品定义表名。 */
    private String itemTable;

    /** 物品定义主键。 */
    private Long itemId;

    /** 收费项定义主键。 */
    private Long chargeItemDefinitionId;

    /** 库房主键。 */
    private Long warehouseId;

    /** 供应商标识。 */
    private Long supplierId;

    /** 项目编码。 */
    private String productBusNo;

    /** 项目名称。 */
    private String name;

    /** 商品名称。 */
    private String merchandiseName;

    /** 规格。 */
    private String totalVolume;

    /** 厂家/产地。 */
    private String manufacturer;

    /** 批准文号。 */
    private String approvalNumber;

    /** 医保码。 */
    private String ybNo;

    /** 追溯码。 */
    private String traceNo;

    /** 拼音。 */
    private String pyStr;

    /** 五笔。 */
    private String wbStr;

    /** 生产批号。 */
    private String lotNumber;

    /** 账面数量（拆零最小单位）。 */
    private BigDecimal quantity;

    /** 拆零↔包装倍数。 */
    private BigDecimal packageUnitRatio;

    /** 包装单位字典 code。 */
    private String packageUnitCode;

    /** 批号进价（包装整包）。 */
    private BigDecimal lotNumberCostAmount;

    /** 批号售价（包装整包）。 */
    private BigDecimal lotNumberPriceAmount;

    /** 进价行总额。 */
    private BigDecimal lineCostTotal;

    /** 售价行总额。 */
    private BigDecimal linePriceTotal;

    /** 生产日期。 */
    private LocalDateTime productionDate;

    /** 效期。 */
    private LocalDateTime expirationDate;

    /** 剩余过期天数。 */
    private Integer remainingDays;

    /** 供应状态（PublicationStatus code）。 */
    private String inventoryStatusEnum;

    /** 医保等级。 */
    private Integer chrgitmLv;

    /** 药品分类 code。 */
    private String medicationCategoryCode;

    /** 耗材分类 code。 */
    private String deviceCategoryCode;

    /** 剂型 code。 */
    private String doseFormCode;
}
