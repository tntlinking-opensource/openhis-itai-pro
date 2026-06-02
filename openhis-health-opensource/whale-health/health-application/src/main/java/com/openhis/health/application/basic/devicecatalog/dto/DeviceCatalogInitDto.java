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

package com.openhis.health.application.basic.devicecatalog.dto;

import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 耗材目录维护页初始化数据。
 *
 * <p>当前 {@link com.openhis.health.application.basic.devicecatalog.service.DeviceCatalogAppService#init()}
 * 填充计量维度下拉、供应商列表、耗材种类（{@code device_type_code}）等（均为 {@code label/value} 行列表，兼容旧表格数据源）。</p>
 */
@Data
public class DeviceCatalogInitDto {

    /**
     * 五类计量单位统一候选（术语体系 {@code unit_code}，与药品目录 {@code unitCodeOptions} 同口径）。
     */
    private List<Map<String, Object>> unitCodeOptions;

    /** 包装单位（术语体系 {@code purchase_unit}，兼容旧前端；内容与 {@link #unitCodeOptions} 同源）。 */
    private List<Map<String, Object>> packUnits;
    /** 销售单位（术语体系 {@code sales_unit}）。 */
    private List<Map<String, Object>> saleUnits;
    /** 最小/拆零单位（术语体系 {@code split_unit}）。 */
    private List<Map<String, Object>> minUnits;
    /** 库存单位（术语体系 {@code stock_unit}）。 */
    private List<Map<String, Object>> stockUnits;
    /** 剂量单位（术语体系 {@code dose_unit}）。 */
    private List<Map<String, Object>> doseUnits;

    /** 耗材分类（术语体系 {@code device_category_code}）。 */
    private List<Map<String, Object>> deviceCategories;
    /** 耗材种类（术语体系 {@code device_type_code}）。 */
    private List<Map<String, Object>> equipmentTypes;
    /** 执行机构/管辖机构占位。 */
    private List<Map<String, Object>> exeOrganizations;
    /** 供应商下拉（label/value 行，value 为供应商 id 字符串）。 */
    private List<Map<String, Object>> supplierListOptions;
    /** 医保分类字典占位。 */
    private List<Map<String, Object>> ybCategories;
    /** 财务类型（术语体系 {@code fin_type_code}，与药品目录初始化一致）。 */
    private List<Map<String, Object>> financialTypes;
    /** 医保等级字典占位。 */
    private List<Map<String, Object>> ybGrades;
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
        dto.setUnitCodeOptions(Collections.emptyList());
        dto.setPackUnits(Collections.emptyList());
        dto.setSaleUnits(Collections.emptyList());
        dto.setMinUnits(Collections.emptyList());
        dto.setStockUnits(Collections.emptyList());
        dto.setDoseUnits(Collections.emptyList());
        dto.setYbCategories(Collections.emptyList());
        dto.setFinancialTypes(Collections.emptyList());
        dto.setYbGrades(Collections.emptyList());
        dto.setLocations(Collections.emptyList());
        return dto;
    }
}
