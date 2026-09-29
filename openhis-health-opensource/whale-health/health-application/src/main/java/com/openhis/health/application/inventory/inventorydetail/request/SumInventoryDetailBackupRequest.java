/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 备份库存底部汇总入参（IF-INV-DTL-05）；筛选与分页查询同构，不含分页键。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "备份库存明细汇总 IF-INV-DTL-05")
public class SumInventoryDetailBackupRequest {
    /** 备份批次单号（backupBusNo）；未传默认当天最新批次。 */
    @Schema(description = "备份批次单号（backupBusNo）；未传默认当天最新批次")
    private String backupBusNo;

    /** 关键字：名称、编码、商品名、拼音、五笔、批号。 */
    @Schema(description = "关键字：名称、编码、商品名、拼音、五笔、批号")
    private String keyword;

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
     * 重点药类型筛选（KeyDrugType code）；仅收窄药品行；空/null 不限。
     */
    @Schema(description = "重点药类型：BASIC/NATIONAL_MONITOR/CENTRAL_PURCHASE/ANTIBIOTIC；null 不限（仅药品）")
    private String keyDrugType;

    /**
     * 药品性质筛选（NARCOTIC/PSYCH_ONE/PSYCH_TWO）；与重点药类型分框，同时非空时 AND。
     */
    @Schema(description = "药品性质：NARCOTIC/PSYCH_ONE/PSYCH_TWO；null 不限（仅药品）")
    private String medicinePropertyCode;

    /** 库存范围：true 数量等于0，false 数量大于0，null 不限。 */
    @Schema(description = "库存范围：true 数量等于0，false 数量大于0，null 不限")
    private Boolean zeroStockOnly;

    /** 近效期窗口天数。 */
    @Schema(description = "近效期窗口天数")
    private Integer expireWithinDays;

    /** 供应状态码多选。 */
    @Schema(description = "供应状态码多选")
    private List<String> inventoryStatusEnums;

    /**
     * 项目类型：药品/耗材定义表名；空/null 不限。
     */
    @Schema(description = "项目类型：med_medication_definition=药品，adm_device_definition=耗材；null 不限")
    private String itemTable;
}
