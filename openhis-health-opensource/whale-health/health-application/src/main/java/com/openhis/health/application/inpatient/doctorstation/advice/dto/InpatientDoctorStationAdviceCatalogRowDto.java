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

package com.openhis.health.application.inpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * IF-IP-DOR-11：住院医生站「医嘱开立目录」分页行契约。
 *
 * <p>主数据来自 {@link ProductDefinitionDisplayDto}；
 * 单价来自 {@code adm_charge_item_definition} 批量查询（读模型不落库金额）；五类单位以 {@link #unitBands} 结构化下发。
 * 产品类型由 {@link #productTableName} 表达（如药品/耗材/诊疗定义表名）；批号见 {@link #inventoryList} 明细行。</p>
 */
@Data
@Accessors(chain = true)
/** 医生站医嘱开立目录行。 */
@Schema(description = "医生站医嘱开立目录行")
public class InpatientDoctorStationAdviceCatalogRowDto {

    /** 目录锚点：产品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "产品定义主键")
    private Long productDefinitionId;

    /** 目录锚点：产品来源物理表名。 */
    @Schema(description = "产品来源表名")
    private String productTableName;

    /** 业务编码（读模型 {@code product_bus_no}）。 */
    @Schema(description = "业务编码")
    private String busNo;

    /** 展示名称（读模型 {@code product_name}）。 */
    @Schema(description = "展示名称")
    private String name;

    /** 名称拼音检索码（读模型 {@code py_str}）。 */
    @Schema(description = "拼音检索码")
    private String pyCode;

    /** 名称五笔检索码（读模型 {@code wb_str}）。 */
    @Schema(description = "五笔检索码")
    private String wbCode;

    /** 目录类别编码。 */
    @Schema(description = "目录类别编码")
    private String categoryCode;

    /** 目录类别展示名（读模型 {@code category_code_dict_text}）。 */
    @Schema(description = "目录类别展示名")
    private String categoryCodeDictText;

    /**
     * 计价主单位编码：包装单位（{@code package_unit_code}），禁止跨槽回落。
     */
    @Schema(description = "计价主单位编码（包装单位）")
    private String unitCode;

    @Schema(description = "计价主单位展示名")
    private String unitName;

    /**
     * 五类单位槽（采购/拆零/销售/库存/剂量）及相对拆零倍数；来源读模型单位列与换算比列。
     */
    @Schema(description = "五类单位槽列表")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    /** 默认用药频次编码（药品读模型投影）。 */
    @Schema(description = "默认频次编码")
    private String rateCode;

    /** 默认频次展示名。 */
    @Schema(description = "默认频次展示名")
    private String rateCodeDictText;

    /** 默认用法编码（药品）。 */
    @Schema(description = "默认用法编码")
    private String methodCode;

    /** 默认用法展示名。 */
    @Schema(description = "默认用法展示名")
    private String methodCodeDictText;

    /** 剂型编码（药品）。 */
    @Schema(description = "剂型编码")
    private String doseFormCode;

    /** 剂型展示名。 */
    @Schema(description = "剂型展示名")
    private String doseFormCodeDictText;

    /** 是否限制使用。 */
    @Schema(description = "是否限制使用")
    private Boolean restrictedFlag;

    /** 限制使用范围说明。 */
    @Schema(description = "限制使用范围说明")
    private String restrictedScope;

    /** 限制用药等级枚举编码。 */
    @Schema(description = "限制用药等级编码")
    private String restrictedEnum;

    /** 限制用药等级展示名。 */
    @Schema(description = "限制用药等级展示名")
    private String restrictedEnumDictText;

    /** 拆零属性持久化码。 */
    @Schema(description = "拆零属性码")
    private String partAttributeEnum;

    /** 拆零属性术语展示名。 */
    @Schema(description = "拆零属性展示名")
    private String partAttributeEnumDictText;

    /** 住院临时拆分属性码。 */
    @Schema(description = "住院临时拆分属性码")
    private String thoPartAttributeEnum;

    /** 住院临时拆分属性术语展示名。 */
    @Schema(description = "住院临时拆分属性展示名")
    private String thoPartAttributeEnumDictText;

    /** 包装规格（读模型 {@code total_volume}）。 */
    @Schema(description = "包装规格")
    private String totalVolume;

    /** 诊疗活动类型 code（字符串化整型）。 */
    @Schema(description = "诊疗活动类型 code")
    private String activityTypeCode;

    /** 是否皮试药（读模型标志）。 */
    @Schema(description = "皮试标志")
    private Boolean skinTestFlag;

    /** 是否注射给药相关（读模型标志）。 */
    @Schema(description = "注射标志")
    private Boolean injectFlag;

    /** 默认执行科室主键（读模型 {@code location_id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 默认执行科室主键。 */
    @Schema(description = "默认执行科室主键")
    private Long defaultPerformOrgId;

    /** 默认执行科室名称。 */
    @Schema(description = "默认执行科室名称")
    private String defaultPerformOrgName;

    /** 默认发药/发材库房（IF-IP-DOR-11 读侧展示；住院写路径不回传）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 默认发药/发材库房 id。 */
    @Schema(description = "默认发药/发材库房 id")
    private Long defaultDispenseLocationId;

    /** 默认库房名称（当前装配不解析名称，预留与库位主数据对齐）。 */
    @Schema(description = "默认库房名称")
    private String defaultDispenseLocationName;

    /** 默认发点下可开总量（药/材预减后汇总，单位与目录库存列展示单位一致，见 {@code ProductDefinitionCatalogUnitBandsAssembler#convertDoctorStationCatalogAvailableQuantity}）。 */
    @Schema(description = "默认发点下可开总量展示列（展示单位，非拆零小单位）")
    private BigDecimal availableQuantity;

    /** 预减后的库存行列表（仅药/材；批号在明细行）。 */
    @Schema(description = "预减后的库存行列表（仅药/材）")
    private List<InpatientDoctorStationAdviceInventoryRowDto> inventoryList;

    /**
     * 目录展示单价（元）：诊疗等为择优定价行；药品/耗材在存在 {@link #inventoryList} 时与<strong>首条库存行批号</strong>对应的
     * {@code LOT_NUMBER_PRICE} 一致（批号不同价不同）；无库存行时仍为实例级择优口径。
     */
    @Schema(description = "目录展示单价（元）；药耗有库存时与首条库存批号的批号售价一致")
    private BigDecimal unitPrice;

    /** 与 {@link #unitPrice} 对应的收费项定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费项定义主键")
    private Long chargeItemDefinitionId;
}
