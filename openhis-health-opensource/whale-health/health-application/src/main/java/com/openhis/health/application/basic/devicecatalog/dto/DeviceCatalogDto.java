/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 耗材（器材）目录行 DTO（列表与详情共用扁平形状）。
 *
 * <p>字段名中带 {@code _dictText} / {@code _enumText} 后缀的为前端展示用冗余文案。</p>
 */
@Data
public class DeviceCatalogDto {

    /** 主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 业务编号。 */
    private String busNo;
    /** 耗材名称。 */
    private String name;
    /** 拼音检索码（器材名称维度）。 */
    private String pyStr;
    /** 五笔检索码（器材名称维度）。 */
    private String wbStr;
    /** 商品名称。 */
    private String merchandiseName;

    /** 分类编码。 */
    private String categoryCode;
    /** 分类展示文案。 */
    private String categoryCode_dictText;

    /** 类型编码。 */
    private String typeCode;
    /** 类型字典展示文案。 */
    private String typeCode_dictText;

    /** 采购单位编码（持久化 {@code purchase_unit_code}）。 */
    private String purchaseUnitCode;
    /** 采购单位字典展示文案。 */
    private String purchaseUnitCode_dictText;

    /**
     * 采购单位历史字段名（与 {@link #purchaseUnitCode} 同值，兼容旧前端）。
     */
    private String unitCode;
    /**
     * 采购单位历史展示字段（与 {@link #purchaseUnitCode_dictText} 同值）。
     */
    private String unitCode_dictText;

    /** 包装规格。 */
    private String totalVolume;

    /** 规格（列表展示，与 {@link #totalVolume} 同源）。 */
    private String specification;

    /** 商品名拼音检索码。 */
    private String merchandisePyStr;

    /** 商品名五笔检索码。 */
    private String merchandiseWbStr;

    /** 默认门诊/就诊单位编码。 */
    private String defEncounterUnitCode;
    /** 默认门诊/就诊单位字典展示文案。 */
    private String defEncounterUnitCode_dictText;

    /** 默认住院单位编码。 */
    private String defInhospitalUnitCode;

    /** 剂量单位编码。 */
    private String doseUnitCode;
    /** 剂量单位字典展示文案。 */
    private String doseUnitCode_dictText;
    /** 剂量→拆零倍数。 */
    private BigDecimal doseUnitRatio;
    /** 采购→拆零倍数。 */
    private BigDecimal purchaseUnitRatio;
    /** 库存→拆零倍数。 */
    private BigDecimal stockUnitRatio;
    /** 销售→拆零倍数。 */
    private BigDecimal packageUnitRatio;

    /**
     * 追溯码所贴单位编码（术语 {@code unit_code}；可游离于五类业务单位之外）。
     *
     * <p>耗材可选；有值时同意入库写入追溯码主档。</p>
     */
    private String traceUnitCode;

    /** 追溯码单位字典展示文案。 */
    private String traceUnitCode_dictText;

    /**
     * 1 个追溯码单位对应的拆零数量；与 {@link #traceUnitCode} 成对。
     */
    private BigDecimal traceUnitRatio;

    /** 拆零单位编码（持久化 {@code split_unit_code}）。 */
    private String splitUnitCode;
    /** 拆零单位字典展示文案。 */
    private String splitUnitCode_dictText;

    /**
     * 拆零单位历史字段名（与 {@link #splitUnitCode} 同值，兼容旧前端）。
     */
    private String minUnitCode;
    /**
     * 拆零单位历史展示字段（与 {@link #splitUnitCode_dictText} 同值）。
     */
    private String minUnitCode_dictText;

    /** 销售单位编码。 */
    private String packageUnitCode;
    /** 销售单位字典展示文案。 */
    private String packageUnitCode_dictText;

    /** 库存单位编码。 */
    private String stockUnitCode;
    /** 库存单位字典展示文案。 */
    private String stockUnitCode_dictText;

    /** 管理机构 ID。 */
    private Long orgId;
    /** 管理机构名称冗余展示。 */
    private String orgId_dictText;

    /** 存放位置 ID。 */
    private Long locationId;
    /** 存放点位名称冗余展示。 */
    private String locationId_dictText;

    /** 型号。 */
    private String modelNumber;

    /** 是否高值耗材。 */
    private Boolean hvcmFlag;
    /** 高值耗材标识枚举中文说明。 */
    private String hvcmFlag_enumText;

    /** 收费耗材标记（true=收费；与界面「收费耗材」勾选同向）。 */
    private Boolean chargeableFlag;
    /** 病案结算类型编码。 */
    private String mrSettlementTypeCode;

    /** 批准文号。 */
    private String approvalNumber;

    /** 是否医保相关（字典语义）。 */
    private Boolean ybFlag;
    /** 医保码。 */
    private String ybNo;
    /** 医保是否已对码。 */
    private Boolean ybMatchFlag;
    /** 医保收费等级。 */
    private Integer chrgitmLv;

    /** 成分说明。 */
    private String substanceText;

    /** 生产厂家 ID。 */
    private Long manufacturerId;
    /** 厂家名称文本。 */
    private String manufacturerText;

    /** 供应商 ID。 */
    private Long supplyId;
    /** 供应商名称冗余展示。 */
    private String supplyId_dictText;

    /** 是否过敏原相关。 */
    private Boolean allergenFlag;

    /** 财务分类编码。 */
    private String itemTypeCode;
    /** 财务分类字典展示文案。 */
    private String itemTypeCode_dictText;

    /** 医保类别编码。 */
    private String ybType;
    /** 医保类别字典展示文案。 */
    private String ybType_dictText;

    /** 管辖范围说明。 */
    private String jurisdiction;

    /**
     * 采购参考价（维护态 {@code PURCHASE}，元/包装单位；真正进价见批号 {@code LOT_NUMBER_COST}）。
     */
    private BigDecimal purchasePrice;
    /**
     * 销售参考价（维护态 {@code SALE}，元/包装单位；真正售价见批号 {@code LOT_NUMBER_PRICE}）。
     */
    private BigDecimal retailPrice;
    /** 限价（元）。 */
    private BigDecimal maximumRetailPrice;

    /** 单次最小用量等业务限量（依前端口径）。 */
    private BigDecimal itemMinQuantity;
    /** 单次或库存维度最大用量/限量上限。 */
    private BigDecimal itemMaxQuantity;

    /** 描述。 */
    private String description;

    /** 编辑业务码（更新请求必填；详情加载时由应用层填入服务端解析结果）。 */
    private String editabilityCode;

    /** 编辑业务码说明（维护页提示）。 */
    private String editabilityMessage;

    /** 耗材定义生命周期状态（PublicationStatus 编码）。 */
    private String statusEnum;
    /** 生命周期状态中文展示。 */
    private String statusEnum_enumText;

    /** 目录启用标记（读模型）。 */
    private Boolean activeFlag;
    /** 目录启用中文标签。 */
    private String activeFlagText;
}
