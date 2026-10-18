/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.medicationcatalog.dto;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 药品目录维护页初始化：术语与各业务下拉选项集合。
 *
 * <p>由 {@link com.zxplat.application.basic.medicationcatalog.service.MedicationCatalogAppService#getMedicationInit()} 经
 *
 */
@Data
@Accessors(chain = true)
@Schema(title = "MedicationManageInitDto", description = "药品目录页面初始化所需的各种字典/选项集合")
public class MedicationManageInitDto {

    @Schema(description = "状态选项列表")
    /** 适用范围选项列表。 */
    private List<OptionDto> statusFlagOptions;

    @Schema(description = "适用范围选项列表")
    private List<OptionDto> domainFlagOptions;

    /**
     * 供应商下拉选项（兼容字段；药品目录维护已不再下发进货供应商，恒为 null/空）。
     */
    @Schema(description = "供应商下拉选项（已废弃，不再下发）")
    private List<OptionDto> supplierListOptions;

    /**
     * 生产厂家下拉（{@code cus_supplier.type_enum=1} 启用态；value=id，label=name）。
     */
    @Schema(description = "生产厂家下拉选项")
    private List<OptionDto> manufacturerOptions;

    /** 页面药品分类树（value/label/children）。 */
    @Schema(description = "页面药品分类树（value/label/children）")
    private List<OptionDto> medicationCategoryCodeOptions;

    /** 计量单位选项（术语 {@code unit_code}；采购/拆零/包装/库存共用）。 */
    @Schema(description = "计量单位字典选项（unit_code）")
    private List<OptionDto> unitCodeOptions;

    /** 剂量单位（术语 {@code dose_unit}）。 */
    @Schema(description = "剂量单位字典选项")
    private List<OptionDto> doseUnitCodeOptions;

    /** 是/否状态选项。 */
    @Schema(description = "是/否状态选项")
    private List<OptionDto> statusWeatherOptions;

    /** 权限限制选项。 */
    @Schema(description = "权限限制选项")
    private List<OptionDto> statusRestrictedOptions;

    /** 拆分属性选项。 */
    @Schema(description = "拆分属性选项")
    private List<OptionDto> partAttributeEnumOptions;

    /** 住院临时医嘱拆分属性选项。 */
    @Schema(description = "住院临时医嘱拆分属性选项")
    private List<OptionDto> tempOrderSplitPropertyEnumOptions;

    /** 医保等级选项。 */
    @Schema(description = "医保等级选项")
    private List<OptionDto> chrgitmLvOptions;

    /** 病案结算类型（术语 {@code mr_settlement_type_code}）。 */
    private List<OptionDto> mrSettlementTypeCodeOptions;

    /**
     * 结余留用批次号下拉（术语 {@code surplus_retention_batch_code}；title=结余留用批次号）。
     */
    @Schema(description = "结余留用批次号字典选项")
    private List<OptionDto> surplusRetentionBatchCodeOptions;

    /**
     * 体系/系统分类选项：系统级别的分类字典列表（例如分类体系、科室体系等）
     */
    @Schema(description = "体系/系统分类字典选项")
    private List<OptionDto> systemCategoriesOptions;

    /**
     * 药品属性选项：药品的属性字典列表（如处方/非处方、OTC/非OTC 等）
     */
    @Schema(description = "药品属性字典选项")
    private List<OptionDto> medicinePropertiesOptions;

    /**
     * 剂型代码选项：药品剂型（片剂、胶囊、注射液等）字典列表
     */
    @Schema(description = "剂型代码字典选项")
    private List<OptionDto> doseFormCodeOptions;

    /**
     * 收费项目类型选项：药品收费/计费类型字典列表
     */
    @Schema(description = "收费项目类型字典选项")
    private List<OptionDto> medChrgitmTypeOptions;

    /**
     * 费率代码选项：与计费相关的费率或比例字典列表
     */
    @Schema(description = "费率代码字典选项")
    private List<OptionDto> rateCodeOptions;

    /**
     * 给药途径/方法选项：给药方式（口服、静脉、皮下注射等）字典列表
     */
    @Schema(description = "给药途径/方法字典选项")
    private List<OptionDto> methodCodeOptions;

    /**
     * 资金/财务类型选项：与财务分类相关的字典列表（用于费用归类）
     */
    @Schema(description = "财务/资金类型字典选项")
    private List<OptionDto> finTypeCodeOptions;

    /**
     * 抗菌药物类型选项：抗菌药物分类字典列表（用于抗菌等级/管理）
     */
    @Schema(description = "抗菌药物类型字典选项")
    private List<OptionDto> antibioticTypeCodeOptions;

    /**
     * DDD（定义日剂量）代码选项：用于药物剂量分析的 DDD 字典列表
     */
    @Schema(description = "DDD（定义日剂量）代码字典选项")
    private List<OptionDto> dddCodeOptions;

    /**
     * DDD（限定日剂量）单位选项：术语 {@code ddd_unit}，与计量 {@code unit_code}、剂量 {@code dose_unit} 分离。
     */
    @Schema(description = "DDD（限定日剂量）单位字典选项")
    private List<OptionDto> dddUnitCodeOptions;

    /**
     * 给药途径/剂量来源选项：剂量来源或剂量单位相关的字典列表
     */
    @Schema(description = "剂量来源/给药途径相关字典选项")
    private List<OptionDto> doseFromCodeOptions;

    /**
     * 处方标识选项：是否处方药等标识字典列表（用于前端过滤或展示）
     */
    @Schema(description = "处方标识字典选项")
    private List<OptionDto> rxFlagOptions;

    /**
     * 用法/用量说明选项：用药说明或给药指导的字典列表（供前端下拉或模板选择）
     */
    @Schema(description = "用法/用量说明字典选项")
    private List<OptionDto> dosageInstructionOptions;

    /**
     * 库房信息选项：与药品存储相关的库房或仓库字典列表（用于选择存放位置）
     */
    @Schema(description = "库房信息")
    private List<OptionDto> locationOptions;

}
