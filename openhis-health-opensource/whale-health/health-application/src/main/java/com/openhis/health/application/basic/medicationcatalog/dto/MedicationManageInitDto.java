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

/* (C)2026 */
package com.openhis.health.application.basic.medicationcatalog.dto;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 药品目录维护页初始化：术语与各业务下拉选项集合。
 *
 * <p>由 {@link com.openhis.health.application.basic.medicationcatalog.service.MedicationCatalogAppService#getMedicationInit()} 经
 *
 */
@Data
@Accessors(chain = true)
@Schema(title = "MedicationManageInitDto", description = "药品目录页面初始化所需的各种字典/选项集合")
public class MedicationManageInitDto {

    @Schema(description = "状态选项列表")
    private List<OptionDto> statusFlagOptions;

    @Schema(description = "适用范围选项列表")
    private List<OptionDto> domainFlagOptions;

    /** 主数据供应商列表，value 多为供应商主键字符串。 */
    @Schema(description = "供应商下拉选项（value=id, label=名称）")
    private List<OptionDto> supplierListOptions;

    /** 前端分类树（支持 children 层级）。 */
    @Schema(description = "页面药品分类树（value/label/children）")
    private List<OptionDto> medicationCategoryCodeOptions;

    /** 计量与包装相关单位术语选项。 */
    @Schema(description = "单位编码选项（value/label）")
    private List<OptionDto> unitCodeOptions;

    /** 是否类字典（如启用、皮试）统一数据源。 */
    @Schema(description = "是/否状态选项")
    private List<OptionDto> statusWeatherOptions;

    /** 处方权或专科权限限制相关字典。 */
    @Schema(description = "权限限制选项")
    private List<OptionDto> statusRestrictedOptions;

    /** 医嘱拆分/摆药属性字典。 */
    @Schema(description = "拆分属性选项")
    private List<OptionDto> partAttributeEnumOptions;

    /** 住院临时医嘱专用的拆分策略字典。 */
    @Schema(description = "住院临时医嘱拆分属性选项")
    private List<OptionDto> tempOrderSplitPropertyEnumOptions;

    /** 医保甲乙丙类等等级下拉。 */
    @Schema(description = "医保等级选项")
    private List<OptionDto> chrgitmLvOptions;

    /**
     * 药品分类选项：前端下拉或树形选择使用的药品分类字典列表（dictCode / dictLabel / dictValue 等）
     */
    @Schema(description = "药品分类字典选项（用于下拉/筛选）")
    private List<OptionDto> medCategoryCodeOptions;

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
