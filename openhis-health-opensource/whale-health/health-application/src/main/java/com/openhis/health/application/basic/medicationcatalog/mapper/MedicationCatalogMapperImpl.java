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

package com.openhis.health.application.basic.medicationcatalog.mapper;

import com.openhis.health.application.basic.medicationcatalog.dto.MedicationDetailDto;
import com.openhis.health.application.basic.medicationcatalog.dto.MedicationDto;
import com.openhis.health.application.basic.medicationcatalog.dto.MedicationManageInitDto;
import com.openhis.health.application.basic.medicationcatalog.request.CreateMedicationCatalogRequest;
import com.openhis.health.application.basic.medicationcatalog.request.UpdateMedicationCatalogRequest;
import com.openhis.health.module.common.assembler.command.BatchCommandItemAssembler;
import com.openhis.health.domain.medication.medicationdefinition.command.ActivateMedicationDefinitionsCommand;
import com.openhis.health.domain.medication.medicationdefinition.command.CreateMedicationDefinitionCommand;
import com.openhis.health.domain.medication.medicationdefinition.command.DiscardMedicationDefinitionCommand;
import com.openhis.health.domain.medication.medicationdefinition.command.EditMedicationDefinitionCommand;
import com.openhis.health.domain.medication.medicationdefinition.command.RetireMedicationDefinitionsCommand;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.MedicationDefinitionDetailQueryModel;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.MedicationDefinitionInitQueryModel;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.MedicationDefinitionPageQueryModel;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.CatalogPresentationUtil;
import com.openhis.health.domain.share.utils.EnumUtil;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link MedicationCatalogMapper} 手写实现：药品目录请求、读模型与领域命令/DTO 之间的字段搬运。
 *
 * <p>不负责业务校验与定价合并；批量命令装配须保持与领域 {@code *Command} 项形状一致。</p>
 */
@Component
public class MedicationCatalogMapperImpl implements MedicationCatalogMapper {

    /**
     * 创建药品目录请求转换为领域创建命令。
     *
     * @param request 创建请求
     * @return 创建命令
     */
    @Override
    public CreateMedicationDefinitionCommand toCreateCommand(CreateMedicationCatalogRequest request) {
        CreateMedicationDefinitionCommand command = new CreateMedicationDefinitionCommand();
        CreateMedicationDefinitionCommand.CreateMedicationDefinitionItemCommand item =
                new CreateMedicationDefinitionCommand.CreateMedicationDefinitionItemCommand();
        item.setBusNo(request.getBusNo());
        item.setName(request.getName());
        item.setVersion(request.getVersion());
        item.setMerchandiseName(request.getMerchandiseName());
        item.setPyStr(request.getPyStr());
        item.setWbStr(request.getWbStr());
        item.setMerchandisePyStr(request.getMerchandisePyStr());
        item.setMerchandiseWbStr(request.getMerchandiseWbStr());
        item.setCategoryCode(request.getCategoryCode());
        item.setPurchaseUnitCode(request.getPurchaseUnitCode());
        item.setSplitUnitCode(request.getSplitUnitCode());
        item.setPackageUnitCode(request.getPackageUnitCode());
        item.setStockUnitCode(request.getStockUnitCode());
        item.setDoseUnitCode(request.getDoseUnitCode());
        item.setDoseUnitRatio(request.getDoseUnitRatio());
        item.setPurchaseUnitRatio(request.getPurchaseUnitRatio());
        item.setStockUnitRatio(request.getStockUnitRatio());
        item.setPackageUnitRatio(request.getPackageUnitRatio());
        item.setOrgId(request.getOrgId());
        item.setLocationId(request.getLocationId());
        item.setDoseFormCode(request.getDoseFormCode());
        item.setMethodCode(request.getMethodCode());
        item.setRateCode(request.getRateCode());
        item.setYbNo(request.getYbNo());
        item.setYbMatchFlag(request.getYbMatchFlag());
        item.setChrgitmLv(request.getChrgitmLv());
        item.setPharmacologyCategoryCode(request.getPharmacologyCategoryCode());
        item.setManufacturerId(request.getManufacturerId());
        item.setManufacturerText(request.getManufacturerText());
        item.setSupplyId(request.getSupplyId());
        item.setApprovalNumber(request.getApprovalNumber());
        item.setNationalDrugCode(request.getNationalDrugCode());
        item.setTotalVolume(request.getTotalVolume());
        item.setUsageLimit(request.getUsageLimit());
        item.setDose(request.getDose());
        item.setMaxUnit(request.getMaxUnit());
        item.setDosageInstruction(request.getDosageInstruction());
        item.setRestrictedScope(request.getRestrictedScope());
        item.setPartAttributeEnum(request.getPartAttributeEnum());
        item.setThoPartAttributeEnum(request.getThoPartAttributeEnum());
        item.setDefEncounterUnitCode(request.getPartAttributeEnum());
        item.setDefInhospitalUnitCode(request.getThoPartAttributeEnum());
        item.setItemMinQuantity(request.getItemMinQuantity());
        item.setItemMaxQuantity(request.getItemMaxQuantity());
        item.setBasicFlag(request.getBasicFlag());
        item.setSkinTestFlag(request.getSkinTestFlag());
        item.setInjectFlag(request.getInjectFlag());
        item.setChildrenFlag(request.getChildrenFlag());
        item.setSelfFlag(request.getSelfFlag());
        item.setActiveFlag(request.getActiveFlag());
        item.setRestrictedFlag(request.getRestrictedFlag());
        item.setAntibioticFlag(request.getAntibioticFlag());
        item.setRxFlag(request.getRxFlag());
        item.setDrug69Code(request.getDrug69Code());
        item.setDefinition(request.getDefinition());
        item.setDddCode(request.getDddCode());
        item.setDddUnitCode(request.getDddUnitCode());
        item.setMinRateCode(request.getMinRateCode());
        item.setMaxRateCode(request.getMaxRateCode());
        command.setItem(item);
        return command;
    }

    /**
     * 更新药品目录请求转换为领域编辑命令。
     *
     * @param request 更新请求
     * @return 编辑命令
     */
    @Override
    public EditMedicationDefinitionCommand toEditCommand(UpdateMedicationCatalogRequest request) {
        EditMedicationDefinitionCommand command = new EditMedicationDefinitionCommand();
        EditMedicationDefinitionCommand.EditMedicationDefinitionItemCommand item =
                new EditMedicationDefinitionCommand.EditMedicationDefinitionItemCommand();
        item.setId(request.getId());
        item.setBusNo(request.getBusNo());
        item.setName(request.getName());
        item.setMerchandiseName(request.getMerchandiseName());
        item.setPyStr(request.getPyStr());
        item.setWbStr(request.getWbStr());
        item.setMerchandisePyStr(request.getMerchandisePyStr());
        item.setMerchandiseWbStr(request.getMerchandiseWbStr());
        item.setCategoryCode(request.getCategoryCode());
        item.setPurchaseUnitCode(request.getPurchaseUnitCode());
        item.setSplitUnitCode(request.getSplitUnitCode());
        item.setPackageUnitCode(request.getPackageUnitCode());
        item.setStockUnitCode(request.getStockUnitCode());
        item.setDoseUnitCode(request.getDoseUnitCode());
        item.setDoseUnitRatio(request.getDoseUnitRatio());
        item.setPurchaseUnitRatio(request.getPurchaseUnitRatio());
        item.setStockUnitRatio(request.getStockUnitRatio());
        item.setPackageUnitRatio(request.getPackageUnitRatio());
        item.setLocationId(request.getLocationId());
        item.setDoseFormCode(request.getDoseFormCode());
        item.setMethodCode(request.getMethodCode());
        item.setRateCode(request.getRateCode());
        item.setDosageInstruction(request.getDosageInstruction());
        item.setYbNo(request.getYbNo());
        item.setYbMatchFlag(request.getYbMatchFlag());
        item.setChrgitmLv(request.getChrgitmLv());
        item.setPharmacologyCategoryCode(request.getPharmacologyCategoryCode());
        item.setManufacturerId(request.getManufacturerId());
        item.setManufacturerText(request.getManufacturerText());
        item.setSupplyId(request.getSupplyId());
        item.setApprovalNumber(request.getApprovalNumber());
        item.setNationalDrugCode(request.getNationalDrugCode());
        item.setTotalVolume(request.getTotalVolume());
        item.setUsageLimit(request.getUsageLimit());
        item.setDose(request.getDose());
        item.setMaxUnit(request.getMaxUnit());
        item.setRestrictedScope(request.getRestrictedScope());
        item.setPartAttributeEnum(request.getPartAttributeEnum());
        item.setThoPartAttributeEnum(request.getThoPartAttributeEnum());
        item.setDefEncounterUnitCode(request.getPartAttributeEnum());
        item.setDefInhospitalUnitCode(request.getThoPartAttributeEnum());
        item.setItemMinQuantity(request.getItemMinQuantity());
        item.setItemMaxQuantity(request.getItemMaxQuantity());
        item.setBasicFlag(request.getBasicFlag());
        item.setSkinTestFlag(request.getSkinTestFlag());
        item.setInjectFlag(request.getInjectFlag());
        item.setChildrenFlag(request.getChildrenFlag());
        item.setSelfFlag(request.getSelfFlag());
        item.setActiveFlag(request.getActiveFlag());
        item.setRestrictedFlag(request.getRestrictedFlag());
        item.setAntibioticFlag(request.getAntibioticFlag());
        item.setRxFlag(request.getRxFlag());
        item.setDrug69Code(request.getDrug69Code());
        item.setDefinition(request.getDefinition());
        item.setDddCode(request.getDddCode());
        item.setDddUnitCode(request.getDddUnitCode());
        item.setMinRateCode(request.getMinRateCode());
        item.setMaxRateCode(request.getMaxRateCode());
        command.setItem(item);
        return command;
    }

    /**
     * 主键列表转换为批量停用命令。
     *
     * @param ids 药品主键列表
     * @return 停用命令
     */
    @Override
    public RetireMedicationDefinitionsCommand toRetireCommand(List<Long> ids) {
        RetireMedicationDefinitionsCommand command = new RetireMedicationDefinitionsCommand();
        List<RetireMedicationDefinitionsCommand.RetireMedicationDefinitionsItemCommand> items =
            BatchCommandItemAssembler.fromLongIds(
                ids,
                RetireMedicationDefinitionsCommand.RetireMedicationDefinitionsItemCommand::new,
                RetireMedicationDefinitionsCommand.RetireMedicationDefinitionsItemCommand::setId);
        command.setItems(items);
        return command;
    }

    /**
     * 主键列表转换为批量启用命令。
     *
     * @param ids 药品主键列表
     * @return 启用命令
     */
    @Override
    public ActivateMedicationDefinitionsCommand toActivateCommand(List<Long> ids) {
        ActivateMedicationDefinitionsCommand command = new ActivateMedicationDefinitionsCommand();
        List<ActivateMedicationDefinitionsCommand.ActivateMedicationDefinitionsItemCommand> items =
            BatchCommandItemAssembler.fromLongIds(
                ids,
                ActivateMedicationDefinitionsCommand.ActivateMedicationDefinitionsItemCommand::new,
                ActivateMedicationDefinitionsCommand.ActivateMedicationDefinitionsItemCommand::setId);
        command.setItems(items);
        return command;
    }

    /**
     * 主键列表转换为批量废弃命令。
     *
     * @param ids 药品主键列表
     * @return 废弃命令
     */
    @Override
    public DiscardMedicationDefinitionCommand toDiscardCommand(List<Long> ids) {
        DiscardMedicationDefinitionCommand command = new DiscardMedicationDefinitionCommand();
        List<DiscardMedicationDefinitionCommand.DiscardMedicationDefinitionItemCommand> items =
            BatchCommandItemAssembler.fromLongIds(
                ids,
                DiscardMedicationDefinitionCommand.DiscardMedicationDefinitionItemCommand::new,
                DiscardMedicationDefinitionCommand.DiscardMedicationDefinitionItemCommand::setId);
        command.setItems(items);
        return command;
    }

    /**
     * 初始化查询模型转换为页面初始化 DTO。
     *
     * @param model 初始化查询模型
     * @return 初始化 DTO
     */
    @Override
    public MedicationManageInitDto toInitDto(MedicationDefinitionInitQueryModel model) {
        MedicationManageInitDto dto = new MedicationManageInitDto();
        if (model == null) {
            return dto;
        }
        dto.setMedicationCategoryCodeOptions(model.getCategoryOptions());
        dto.setStatusFlagOptions(model.getStatusFlagOptions());
        dto.setDomainFlagOptions(model.getDomainFlagOptions());
        dto.setSupplierListOptions(model.getSupplierListOptions());
        dto.setStatusWeatherOptions(model.getWeatherOptions());
        dto.setStatusRestrictedOptions(model.getRestrictedOptions());
        dto.setPartAttributeEnumOptions(model.getPartAttributeOptions());
        dto.setTempOrderSplitPropertyEnumOptions(model.getTempOrderSplitPropertyOptions());
        dto.setChrgitmLvOptions(model.getChrgitmLvOptions());
        dto.setSystemCategoriesOptions(model.getSystemCategoryOptions());
        dto.setMedicinePropertiesOptions(model.getMedicinePropertyOptions());
        dto.setDoseFormCodeOptions(model.getDoseFormOptions());
        dto.setMedChrgitmTypeOptions(model.getMedChargeItemTypeOptions());
        dto.setMethodCodeOptions(model.getMethodOptions());
        dto.setFinTypeCodeOptions(model.getFinTypeOptions());
        dto.setAntibioticTypeCodeOptions(model.getAntibioticTypeOptions());
        dto.setDddCodeOptions(model.getDddOptions());
        dto.setDoseFromCodeOptions(model.getDoseFromOptions());
        dto.setRxFlagOptions(model.getRxFlagOptions());
        dto.setDosageInstructionOptions(model.getDosageInstructionOptions());
        dto.setUnitCodeOptions(model.getUnitOptions());
        dto.setRateCodeOptions(model.getFrequencyOptions());
        dto.setLocationOptions(model.getLocationOptions());
        return dto;
    }

    /**
     * 药品分页查询模型转换为列表 DTO。
     *
     * @param model 分页行模型
     * @return 列表 DTO
     */
    @Override
    public MedicationDto toMedicationDto(MedicationDefinitionPageQueryModel model) {
        if (model == null) {
            return null;
        }
        MedicationDto dto = new MedicationDto();
        dto.setId(model.getId());
        dto.setMedicationDefId(model.getId());
        dto.setBusNo(model.getBusNo());
        dto.setName(model.getName());
        dto.setCategoryCode(model.getCategoryCode());
        dto.setStatusEnum(model.getStatusEnum());
        // 发布状态码转中文展示，未知码留空由前端按 code 兜底
        PublicationStatus listPublicationStatus = EnumUtil.fromCode(PublicationStatus.class, model.getStatusEnum());
        if (listPublicationStatus != null) {
            dto.setStatusEnum_enumText(listPublicationStatus.getDisplay());
        } else {
            dto.setStatusEnum_enumText(null);
        }
        dto.setLocationId(model.getLocationId());
        dto.setSupplyId(model.getSupplyId());
        dto.setManufacturerText(model.getManufacturerText());
        dto.setYbNo(model.getYbNo());
        dto.setYbMatchFlag(CatalogPresentationUtil.insuranceMatchedFlag(model.getYbNo()));
        dto.setSpecification(model.getTotalVolume());
        dto.setRxFlag(model.getRxFlag());
        dto.setPurchaseUnitCode(model.getPurchaseUnitCode());
        dto.setSplitUnitCode(model.getSplitUnitCode());
        dto.setPackageUnitCode(model.getPackageUnitCode());
        dto.setStockUnitCode(model.getStockUnitCode());
        dto.setDoseUnitCode(model.getDoseUnitCode());
        dto.setDoseUnitRatio(model.getDoseUnitRatio());
        dto.setPurchaseUnitRatio(model.getPurchaseUnitRatio());
        dto.setStockUnitRatio(model.getStockUnitRatio());
        dto.setPackageUnitRatio(model.getPackageUnitRatio());
        dto.setMaxUnit(model.getMaxUnit());
        if (model.getActiveFlag() != null) {
            CatalogPresentationUtil.fillEnableDisableLabels(
                model.getActiveFlag(), dto::setActiveFlag, dto::setActiveFlagText);
        }
        return dto;
    }

    /**
     * 药品详情查询模型转换为详情 DTO。
     *
     * @param model 详情查询模型
     * @return 详情 DTO
     */
    @Override
    public MedicationDetailDto toMedicationDetailDto(MedicationDefinitionDetailQueryModel model) {
        if (model == null) {
            return null;
        }
        MedicationDetailDto dto = new MedicationDetailDto();
        dto.setId(model.getId());
        dto.setMedicationDefId(model.getId());
        dto.setBusNo(model.getBusNo());
        dto.setName(model.getName());
        dto.setYbNo(model.getYbNo());
        dto.setDoseFormCode(model.getDoseFormCode());
        dto.setDosageInstruction(model.getDosageInstruction());
        dto.setStatusEnum(model.getStatusEnum());
        // 详情页发布状态展示与列表口径一致，经 EnumUtil 解析 code
        PublicationStatus publicationStatus = EnumUtil.fromCode(PublicationStatus.class, model.getStatusEnum());
        if (publicationStatus != null) {
            dto.setStatusEnum_enumText(publicationStatus.getDisplay());
        } else {
            dto.setStatusEnum_enumText(null);
        }
        dto.setDomainEnum(model.getDomainEnum());
        dto.setApprovalNumber(model.getApprovalNumber());
        dto.setBasicFlag(Boolean.TRUE.equals(model.getBasicFlag()));
        dto.setSkinTestFlag(Boolean.TRUE.equals(model.getSkinTestFlag()));
        dto.setInjectFlag(Boolean.TRUE.equals(model.getInjectFlag()));
        dto.setChildrenFlag(Boolean.TRUE.equals(model.getChildrenFlag()));
        dto.setSelfFlag(Boolean.TRUE.equals(model.getSelfFlag()));
        dto.setAntibioticFlag(Boolean.TRUE.equals(model.getAntibioticFlag()));
        dto.setAntibioticCode(model.getAntibioticCode());
        dto.setMinRateCode(model.getMinRateCode());
        dto.setMaxRateCode(model.getMaxRateCode());
        dto.setItemMaxQuantity(model.getItemMaxQuantity());
        dto.setItemMinQuantity(model.getItemMinQuantity());
        dto.setUsageLimit(model.getUsageLimit());
        dto.setYbMatchFlag(model.getYbMatchFlag());
        dto.setNationalDrugCode(model.getNationalDrugCode());
        dto.setDose(model.getDose());
        dto.setMaxUnit(model.getMaxUnit());
        dto.setRxFlag(model.getRxFlag());
        dto.setTotalVolume(model.getTotalVolume());
        dto.setOrgId(model.getOrgId());
        dto.setIngredientItem(model.getIngredientItem());
        dto.setLocationId(model.getLocationId());
        dto.setSupplyId(model.getSupplyId());
        dto.setManufacturerId(model.getManufacturerId());
        dto.setManufacturerText(model.getManufacturerText());
        dto.setPyStr(model.getPyStr());
        dto.setWbStr(model.getWbStr());
        dto.setMerchandiseName(model.getMerchandiseName());
        dto.setMerchandisePyStr(model.getMerchandisePyStr());
        dto.setMerchandiseWbStr(model.getMerchandiseWbStr());
        dto.setCategoryCode(model.getCategoryCode());
        dto.setPurchaseUnitCode(model.getPurchaseUnitCode());
        dto.setSplitUnitCode(model.getSplitUnitCode());
        dto.setPackageUnitCode(model.getPackageUnitCode());
        dto.setStockUnitCode(model.getStockUnitCode());
        dto.setDoseUnitCode(model.getDoseUnitCode());
        dto.setMethodCode(model.getMethodCode());
        dto.setRateCode(model.getRateCode());
        dto.setDoseUnitRatio(model.getDoseUnitRatio());
        dto.setPurchaseUnitRatio(model.getPurchaseUnitRatio());
        dto.setStockUnitRatio(model.getStockUnitRatio());
        dto.setPackageUnitRatio(model.getPackageUnitRatio());
        dto.setDefinition(model.getDefinition());
        dto.setDddCode(model.getDddCode());
        dto.setDddUnitCode(model.getDddUnitCode());
        dto.setPartAttributeEnum(model.getPartAttributeEnum());
        dto.setThoPartAttributeEnum(model.getThoPartAttributeEnum());
        dto.setRestrictedEnum(model.getRestrictedEnum());
        dto.setChrgitmLv(model.getChrgitmLv());
        dto.setVersion(model.getVersion());
        dto.setRestrictedFlag(Boolean.TRUE.equals(model.getRestrictedFlag()));
        dto.setRestrictedScope(model.getRestrictedScope());
        dto.setCharacteristic(model.getCharacteristic());
        dto.setPharmacologyCategoryCode(model.getPharmacologyCategoryCode());
        dto.setDrug69Code(model.getDrug69Code());
        if (model.getActiveFlag() != null) {
            // 启用标志统一映射为前端 1/2 整型及中文文案
            CatalogPresentationUtil.fillEnableDisableLabels(
                model.getActiveFlag(), dto::setActiveFlag, dto::setActiveFlagText);
        }
        return dto;
    }

}
