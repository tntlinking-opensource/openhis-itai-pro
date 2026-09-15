/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdefinition.queries.model;

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 药品目录初始化查询模型。
 */
@Getter
@Setter
public class MedicationDefinitionInitQueryModel {
    /** 状态标志options。 */
    private List<OptionDto> statusFlagOptions;
    /** domain标志options。 */
    private List<OptionDto> domainFlagOptions;
    /** 供应商列表options。 */
    private List<OptionDto> supplierListOptions;
    /** 类别options。 */
    private List<OptionDto> categoryOptions;
    /** weatheroptions。 */
    private List<OptionDto> weatherOptions;
    /** restrictedoptions。 */
    private List<OptionDto> restrictedOptions;
    /** partattributeoptions。 */
    private List<OptionDto> partAttributeOptions;
    /** temp医嘱拆分propertyoptions。 */
    private List<OptionDto> tempOrderSplitPropertyOptions;
    /** chrgitmlvoptions。 */
    private List<OptionDto> chrgitmLvOptions;
    /** 病案结算类型 options。 */
    private List<OptionDto> mrSettlementTypeCodeOptions;
    /**
     * 结余留用批次号 options（术语 {@code surplus_retention_batch_code}）。
     */
    private List<OptionDto> surplusRetentionBatchCodeOptions;
    /** system类别options。 */
    private List<OptionDto> systemCategoryOptions;
    /** medicinepropertyoptions。 */
    private List<OptionDto> medicinePropertyOptions;
    /** doseformoptions。 */
    private List<OptionDto> doseFormOptions;
    /** med收费项类型options。 */
    private List<OptionDto> medChargeItemTypeOptions;
    /** methodoptions。 */
    private List<OptionDto> methodOptions;
    /** fin类型options。 */
    private List<OptionDto> finTypeOptions;
    /** antibiotic类型options。 */
    private List<OptionDto> antibioticTypeOptions;
    /** dddoptions。 */
    private List<OptionDto> dddOptions;
    /** dose来源options。 */
    private List<OptionDto> doseFromOptions;
    /** rx标志options。 */
    private List<OptionDto> rxFlagOptions;
    /** dosageinstructionoptions。 */
    private List<OptionDto> dosageInstructionOptions;
    /** unitoptions。 */
    private List<OptionDto> unitOptions;
    /** frequencyoptions。 */
    private List<OptionDto> frequencyOptions;
    /** 位置options。 */
    private List<OptionDto> locationOptions;
}
