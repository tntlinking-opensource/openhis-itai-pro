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
