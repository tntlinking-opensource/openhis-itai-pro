/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 药品目录详情查询模型。
 */
@Getter
@Setter
public class MedicationDefinitionDetailQueryModel {
    /** 主键。 */
    private Long id;
    /** 业务号。 */
    private String busNo;
    /** 名称。 */
    private String name;
    /** 医保号。 */
    private String ybNo;
    /** doseform编码。 */
    private String doseFormCode;
    /** dosageinstruction。 */
    private String dosageInstruction;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** domain枚举编码。 */
    private String domainEnum;
    /** 审批号。 */
    private String approvalNumber;
    /** basic标志。 */
    private Boolean basicFlag;
    /** 国家重点监控药品标志（勾选=是）。 */
    private Boolean nationalMonitorFlag;
    /** 集采药品标志（勾选=是）。 */
    private Boolean centralPurchaseFlag;
    /** 皮肤试验标志。 */
    private Boolean skinTestFlag;
    /** inject标志。 */
    private Boolean injectFlag;
    /** children标志。 */
    private Boolean childrenFlag;
    /** self标志。 */
    private Boolean selfFlag;
    /** antibiotic标志。 */
    private Boolean antibioticFlag;
    /** 抗生素分类编码。 */
    private String antibioticCode;
    /** minrate编码。 */
    private String minRateCode;
    /** 项max数量。 */
    private BigDecimal itemMaxQuantity;
    /** 项min数量。 */
    private BigDecimal itemMinQuantity;
    /** usagelimit。 */
    private BigDecimal usageLimit;
    /** 医保匹配标志。 */
    private Boolean ybMatchFlag;
    /** nationaldrug编码。 */
    private String nationalDrugCode;
    /** dose。 */
    private BigDecimal dose;
    /** 单次最小剂量。 */
    private BigDecimal minUnit;
    /** maxunit。 */
    private BigDecimal maxUnit;
    /** rx标志。 */
    private Boolean rxFlag;
    /** totalvolume。 */
    private String totalVolume;
    /** 机构主键。 */
    private Long orgId;
    /** 位置主键。 */
    private Long locationId;
    /** 物资主键。 */
    private Long supplyId;
    /** 生产厂家主键（cus_supplier.id，type=生产厂家）。 */
    private Long manufacturerId;
    /**
     * 生产厂家展示名（非持久化列；由 AppService 按 {@link #manufacturerId} 解析供应商名称回填）。
     */
    private String manufacturerText;
    /** 药品拼音码。 */
    private String pyStr;
    /** 药品五笔码。 */
    private String wbStr;
    /** 商品拼音码。 */
    private String merchandisePyStr;
    /** 商品五笔码。 */
    private String merchandiseWbStr;
    /** 商品名。 */
    private String merchandiseName;
    /** 类别编码。 */
    private String categoryCode;
    /** 采购单位编码。 */
    private String purchaseUnitCode;
    /** 拆零单位编码。 */
    private String splitUnitCode;
    /** salesunit编码。 */
    private String packageUnitCode;
    /** 库存unit编码。 */
    private String stockUnitCode;
    /** doseunit编码。 */
    private String doseUnitCode;
    /** method编码。 */
    private String methodCode;
    /** rate编码。 */
    private String rateCode;
    /** doseunitratio。 */
    private BigDecimal doseUnitRatio;
    /** 采购unitratio。 */
    private BigDecimal purchaseUnitRatio;
    /** 库存unitratio。 */
    private BigDecimal stockUnitRatio;
    /** salesunitratio。 */
    private BigDecimal packageUnitRatio;
    /**
     * 追溯码贴码单位（可游离于五类业务单位；药品必填）。
     */
    private String traceUnitCode;
    /**
     * 1 个追溯码单位对应的拆零数量；药品必填且须 &gt; 0。
     */
    private BigDecimal traceUnitRatio;
    /** 药品说明。 */
    private String definition;
    /** DDD编码。 */
    private String dddCode;
    /** DDD单位编码。 */
    private String dddUnitCode;
    /** 最大频次编码。 */
    private String maxRateCode;
    /** 成分项。 */
    private String ingredientItem;
    /** 目录启用（与读模型 active_flag 一致）。 */
    private Boolean activeFlag;
    /** 门诊拆分属性。 */
    private String partAttributeEnum;
    /** 住院临时医嘱拆分属性。 */
    private String thoPartAttributeEnum;
    /** 权限限制枚举。 */
    private String restrictedEnum;
    /** 医保等级。 */
    private Integer chrgitmLv;
    /** 病案结算类型编码。 */
    private String mrSettlementTypeCode;
    /** 药品版本号。 */
    private String version;
    /** 限制使用标志。 */
    private Boolean restrictedFlag;
    /** 限制使用范围说明。 */
    private String restrictedScope;
    /** 产品特性编码。 */
    private Integer characteristic;
    /** 药理作用分类编码。 */
    private String pharmacologyCategoryCode;
    /** 药品69码。 */
    private String drug69Code;
}
