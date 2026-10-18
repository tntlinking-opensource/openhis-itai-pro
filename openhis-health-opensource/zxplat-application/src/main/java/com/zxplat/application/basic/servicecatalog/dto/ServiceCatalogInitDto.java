/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.servicecatalog.dto;

import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 诊疗目录维护页初始化数据：状态下拉、类型、执行科室树、诊疗大类、财务/医保/单位等选项。
 *
 * <p>部分列表由 {@link com.zxplat.application.basic.servicecatalog.service.ServiceCatalogAppService#getDiseaseTreatmentInit()}
 * 从枚举与术语服务填充；{@code diseaseTreatmentCategoryList}、{@code locationOptions} 等字段可按页面需要后续接入。</p>
 */
@Data
@Accessors(chain = true)
public class ServiceCatalogInitDto {
    /** 发布状态选项（来自 {@link com.zxplat.domain.share.enums.PublicationStatus}）。 */
    private List<OptionDto> statusFlagOptions;
    /** 预留：病种/诊疗大类扩展分类列表。 */
    private List<OptionDto> diseaseTreatmentCategoryList;
    /** 可执行科室（组织机构）树形下拉。 */
    private List<OptionDto> exeOrganizations;
    /** 业务类型下拉（术语 {@code activity_type_code}）。 */
    private List<OptionDto> typeEnumOptions;
    /** 诊疗活动大类（activity_category_code 术语）。 */
    private List<OptionDto> diagnosisCategoryOptions;
    /** 是否类通用选项（等，来自 Whether 枚举）。 */
    private List<OptionDto> statusWeatherOption;
    /** 预留：位置/执行地点候选。 */
    private List<OptionDto> locationOptions;
    /** 收费项目医保等级（chrgitm_lv 术语）。 */
    private List<OptionDto> chrgitmLvOptions;
    /** 病案结算类型（mr_settlement_type_code 术语）。 */
    private List<OptionDto> mrSettlementTypeCodeOptions;
    /** 财务分类（fin_type_code 术语）。 */
    private List<OptionDto> finTypeCodeOptions;
    /** 医保收费项目类别（med_chrgitm_type 术语）。 */
    private List<OptionDto> medChrgitmTypeOptions;
    /** 计量单位（unit 术语）。 */
    private List<OptionDto> unitCodeOptions;
}
