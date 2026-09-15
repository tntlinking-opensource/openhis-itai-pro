/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.dto;

import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 耗材目录维护页初始化数据。
 *
 * <p>{@link com.openhis.health.application.basic.devicecatalog.service.DeviceCatalogAppService#init()}
 * 填充计量单位、供应商、生产厂家（type=1）、耗材种类等下拉（{@code label/value} 行）。</p>
 */
@Data
public class DeviceCatalogInitDto {

    /**
     * 计量单位统一候选（术语 {@code unit_code}；采购/拆零/包装/库存共用）。
     */
    private List<Map<String, Object>> unitCodeOptions;

    /** 剂量单位候选（术语 {@code dose_unit}；维护界面可不展示，写路径可默认回落拆零）。 */
    private List<Map<String, Object>> doseUnits;

    /** 耗材分类（术语体系 {@code device_category_code}）。 */
    private List<Map<String, Object>> deviceCategories;
    /** 耗材种类（术语体系 {@code device_type_code}）。 */
    private List<Map<String, Object>> equipmentTypes;
    /** 执行机构/管辖机构占位。 */
    private List<Map<String, Object>> exeOrganizations;
    /** 供应商下拉（label/value 行，value 为供应商 id 字符串）。 */
    private List<Map<String, Object>> supplierListOptions;
    /**
     * 生产厂家下拉（{@code cus_supplier.type_enum=1} 启用态；label/value 行，value=id 字符串）。
     */
    private List<Map<String, Object>> manufacturerOptions;
    /** 医保类别（术语体系 {@code med_chrgitm_type}，与药品/诊疗目录一致）。 */
    private List<Map<String, Object>> ybCategories;
    /** 财务类型（术语体系 {@code fin_type_code}，与药品目录初始化一致）。 */
    private List<Map<String, Object>> financialTypes;
    /** 医保等级（术语体系 {@code chrgitm_lv}，与药品/诊疗目录一致）。 */
    private List<Map<String, Object>> ybGrades;
    /** 病案结算类型（术语体系 {@code mr_settlement_type_code}）。 */
    private List<Map<String, Object>> mrSettlementTypeCodeOptions;
    /** 库房/存放点位占位。 */
    private List<Map<String, Object>> locations;

    /**
     * 构造各列为空列表的初始化 DTO，避免前端访问 undefined。
     *
     * @return 稳定空集合占位实例
     */
    public static DeviceCatalogInitDto empty() {
        DeviceCatalogInitDto dto = new DeviceCatalogInitDto();
        dto.setDeviceCategories(Collections.emptyList());
        dto.setEquipmentTypes(Collections.emptyList());
        dto.setExeOrganizations(Collections.emptyList());
        dto.setSupplierListOptions(Collections.emptyList());
        dto.setManufacturerOptions(Collections.emptyList());
        dto.setUnitCodeOptions(Collections.emptyList());
        dto.setDoseUnits(Collections.emptyList());
        dto.setYbCategories(Collections.emptyList());
        dto.setFinancialTypes(Collections.emptyList());
        dto.setYbGrades(Collections.emptyList());
        dto.setMrSettlementTypeCodeOptions(Collections.emptyList());
        dto.setLocations(Collections.emptyList());
        return dto;
    }
}
