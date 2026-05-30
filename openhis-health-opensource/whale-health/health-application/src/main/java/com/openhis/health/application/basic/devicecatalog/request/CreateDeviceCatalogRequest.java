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

package com.openhis.health.application.basic.devicecatalog.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增耗材目录请求（字段对齐前端建档表单）。
 */
@Data
public class CreateDeviceCatalogRequest {
    /** 耗材业务编号。 */
    private String busNo;
    /** 耗材名称。 */
    private String name;
    /** 拼音检索码（可选；不传则由领域按名称生成）。 */
    private String pyStr;
    /** 五笔检索码（可选；不传则由领域按名称生成）。 */
    private String wbStr;
    /** 商品名称。 */
    private String merchandiseName;
    /** 耗材分类字典编码。 */
    private String categoryCode;
    /** 耗材类型编码。 */
    private String typeCode;
    /**
     * 采购单位编码（持久化列 {@code purchase_unit_code}；与药品目录字段名对齐）。
     */
    private String purchaseUnitCode;
    /** 包装规格（与药品 totalVolume 对齐）。 */
    private String totalVolume;
    /**
     * 拆零单位编码（持久化列 {@code split_unit_code}；与药品目录字段名对齐）。
     */
    private String splitUnitCode;

    /**
     * 采购单位历史别名（{@code purchaseUnitCode} 为空时回落）；新接口请传 {@link #purchaseUnitCode}。
     */
    private String unitCode;
    /**
     * 拆零单位历史别名（{@code splitUnitCode} 为空时回落）；新接口请传 {@link #splitUnitCode}。
     */
    private String minUnitCode;

    /** 默认门诊/就诊单位编码。 */
    private String defEncounterUnitCode;

    /** 默认住院单位编码。 */
    private String defInhospitalUnitCode;
    /** 包装单位编码（界面命名；持久化列 package_unit_code）。 */
    private String packageUnitCode;
    /** 库存单位编码（持久化列 stock_unit_code）。 */
    private String stockUnitCode;
    /** 剂量单位编码（给药/化验口径）。 */
    private String doseUnitCode;
    /** 执行机构主键。 */
    private Long orgId;
    /** 默认存放位置主键。 */
    private Long locationId;
    /** 设备型号。 */
    private String modelNumber;
    /** 是否高值耗材。 */
    private Boolean hvcmFlag;
    /** 批准文号。 */
    private String approvalNumber;
    /** 是否医保耗材。 */
    private Boolean ybFlag;
    /** 医保目录编码。 */
    private String ybNo;
    /** 医保机构编码。 */
    private String ybOrgNo;
    /** 医保对码是否完成。 */
    private Boolean ybMatchFlag;
    /** 医保收费等级。 */
    private Integer chrgitmLv;
    /** 成分或材质说明。 */
    private String substanceText;
    /** 生产厂家主键。 */
    private Long manufacturerId;
    /** 生产厂家名称。 */
    private String manufacturerText;
    /** 供应商主键。 */
    private Long supplyId;
    /** 是否过敏原相关耗材。 */
    private Boolean allergenFlag;
    /** 财务分类编码（收费侧）。 */
    private String itemTypeCode;
    /** 医保类别。 */
    private String ybType;
    /** 管辖范围或适用范围说明。 */
    private String jurisdiction;
    /** 采购价（元）。 */
    private BigDecimal purchasePrice;
    /** 零售价（元）。 */
    private BigDecimal retailPrice;
    /** 最高零售价/限价（元）。 */
    private BigDecimal maximumRetailPrice;
    /** 最小警戒数量。 */
    private BigDecimal itemMinQuantity;
    /** 最大警戒数量。 */
    private BigDecimal itemMaxQuantity;
    /** 补充描述。 */
    private String description;

    /**
     * 五单位换算比：拆零单位为 1，其余为相对倍数（持久化语义见领域层）。
     */
    private BigDecimal doseUnitRatio;
    /** 采购单位相对拆零单位换算比。 */
    private BigDecimal purchaseUnitRatio;
    /** 库存单位相对拆零单位换算比。 */
    private BigDecimal stockUnitRatio;
    /** 销售单位相对拆零单位换算比。 */
    private BigDecimal packageUnitRatio;
}
