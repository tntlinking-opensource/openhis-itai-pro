/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.mapper;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.application.basic.devicecatalog.dto.DeviceCatalogDto;
import com.openhis.health.application.basic.devicecatalog.request.CreateDeviceCatalogRequest;
import com.openhis.health.application.basic.devicecatalog.request.UpdateDeviceCatalogRequest;
import com.openhis.health.application.shared.catalog.BatchCommandItemAssembler;
import com.openhis.health.application.shared.converter.TextValueConverter;
import com.openhis.health.domain.administration.devicedefinition.command.*;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link DeviceCatalogMapper} 手写实现：耗材定义实体、请求体与 {@link DeviceCatalogDto}、领域批量命令项之间的映射。
 *
 * <p>定价金额仍以应用服务合并收费行为准，此处仅搬运主档字段。</p>
 */
@Component
public class DeviceCatalogMapperImpl implements DeviceCatalogMapper {

    /**
     * 耗材定义实体转换为应用层 DTO。
     *
     * <p>单位出站仅 {@code purchaseUnitCode}/{@code splitUnitCode}，不再双写 {@code unitCode}/{@code minUnitCode}。</p>
     *
     * @param entity 领域实体
     * @return 耗材 DTO；为空时返回 {@code null}
     */
    @Override
    public DeviceCatalogDto toDto(DeviceDefinition entity) {
        if (entity == null) {
            return null;
        }
        DeviceCatalogDto dto = new DeviceCatalogDto();
        dto.setId(entity.getId());
        dto.setBusNo(entity.getBusNo());
        dto.setName(entity.getName());
        dto.setPyStr(entity.getPyStr());
        dto.setWbStr(entity.getWbStr());
        dto.setMerchandiseName(entity.getMerchandiseName());

        dto.setCategoryCode(entity.getCategoryCode());
        dto.setTypeCode(entity.getTypeCode());
        // 仅出站 purchaseUnitCode/splitUnitCode，不再双写 unitCode/minUnitCode 别名
        dto.setPurchaseUnitCode(entity.getPurchaseUnitCode());
        dto.setTotalVolume(entity.getTotalVolume());
        dto.setSpecification(entity.getTotalVolume());
        dto.setMerchandisePyStr(entity.getMerchandisePyStr());
        dto.setMerchandiseWbStr(entity.getMerchandiseWbStr());
        dto.setDefEncounterUnitCode(entity.getDefEncounterUnitCode());
        dto.setDefInhospitalUnitCode(entity.getDefInhospitalUnitCode());
        dto.setDoseUnitCode(entity.getDoseUnitCode());
        dto.setDoseUnitRatio(entity.getDoseUnitRatio());
        dto.setPurchaseUnitRatio(entity.getPurchaseUnitRatio());
        dto.setStockUnitRatio(entity.getStockUnitRatio());
        dto.setPackageUnitRatio(entity.getPackageUnitRatio());
        dto.setTraceUnitCode(entity.getTraceUnitCode());
        dto.setTraceUnitRatio(entity.getTraceUnitRatio());
        dto.setSplitUnitCode(entity.getSplitUnitCode());
        dto.setPackageUnitCode(entity.getPackageUnitCode());
        dto.setStockUnitCode(entity.getStockUnitCode());
        dto.setOrgId(entity.getOrgId());
        dto.setLocationId(entity.getLocationId());
        dto.setModelNumber(entity.getModelNumber());

        dto.setHvcmFlag(entity.getHvcmFlag());
        dto.setHvcmFlag_enumText(DeviceCatalogMapperImpl.hvcmCatalogText(entity.getHvcmFlag()));
        dto.setChargeableFlag(entity.getChargeableFlag());
        dto.setMrSettlementTypeCode(entity.getMrSettlementTypeCode());

        dto.setApprovalNumber(entity.getApprovalNumber());
        dto.setYbFlag(entity.getYbFlag());
        dto.setYbNo(entity.getYbNo());
        dto.setYbMatchFlag(entity.getYbMatchFlag());
        dto.setChrgitmLv(entity.getChrgitmLv());
        dto.setSubstanceText(entity.getSubstanceText());

        dto.setManufacturerId(entity.getManufacturerId());
        // 展示名由 AppService 按 manufacturerId 解析供应商名称回填
        dto.setManufacturerText(null);
        dto.setSupplyId(entity.getSupplyId());

        dto.setAllergenFlag(entity.getAllergenFlag());
        dto.setJurisdiction(entity.getJurisdiction());
        dto.setItemMinQuantity(entity.getItemMinQuantity());
        dto.setItemMaxQuantity(entity.getItemMaxQuantity());
        dto.setDescription(entity.getDescription());

        String statusCode = entity.getStatusEnum();
        dto.setStatusEnum(statusCode);
        PublicationStatus status = EnumUtil.fromCode(PublicationStatus.class, statusCode);
        String statusLabel = null;
        if (status != null) {
            statusLabel = status.getDisplay();
        }
        dto.setStatusEnum_enumText(statusLabel);
        return dto;
    }

    /**
     * 新增耗材请求转换为领域创建命令。
     *
     * <p>仅读取请求的 {@code purchaseUnitCode}/{@code splitUnitCode}，无 {@code unitCode}/{@code minUnitCode} 回落。</p>
     *
     * @param request 创建请求
     * @return 创建命令
     */
    @Override
    public CreateDeviceDefinitionCommand toCreateCommand(CreateDeviceCatalogRequest request) {
        CreateDeviceDefinitionCommand cmd = new CreateDeviceDefinitionCommand();
        CreateDeviceDefinitionCommand.Item item = new CreateDeviceDefinitionCommand.Item();
        if (request != null) {
            item.setBusNo(request.getBusNo());
            String name = request.getName();
            if (name == null || name.isBlank()) {
                name = request.getMerchandiseName();
            }
            item.setName(name);
            item.setPyStr(request.getPyStr());
            item.setWbStr(request.getWbStr());
            item.setMerchandiseName(request.getMerchandiseName());
            item.setCategoryCode(request.getCategoryCode());
            item.setTypeCode(request.getTypeCode());
            // 领域命令字段名仍为 unitCode/minUnitCode，语义=采购/拆零
            item.setUnitCode(request.getPurchaseUnitCode());
            item.setTotalVolume(request.getTotalVolume());
            item.setMinUnitCode(request.getSplitUnitCode());
            item.setDefEncounterUnitCode(request.getDefEncounterUnitCode());
            item.setDefInhospitalUnitCode(request.getDefInhospitalUnitCode());
            item.setPackageUnitCode(request.getPackageUnitCode());
            item.setStockUnitCode(request.getStockUnitCode());
            item.setDoseUnitCode(request.getDoseUnitCode());
            item.setOrgId(request.getOrgId());
            item.setLocationId(request.getLocationId());
            item.setModelNumber(request.getModelNumber());
            item.setHvcmFlag(DeviceCatalogMapperImpl.persistBoolAsInt(request.getHvcmFlag()));
            item.setChargeableFlag(DeviceCatalogMapperImpl.persistBoolAsInt(request.getChargeableFlag()));
            item.setMrSettlementTypeCode(request.getMrSettlementTypeCode());
            item.setApprovalNumber(request.getApprovalNumber());
            item.setYbFlag(DeviceCatalogMapperImpl.persistBoolAsInt(request.getYbFlag()));
            item.setYbNo(request.getYbNo());
            item.setYbMatchFlag(DeviceCatalogMapperImpl.persistBoolAsInt(request.getYbMatchFlag()));
            item.setChrgitmLv(request.getChrgitmLv());
            item.setSubstanceText(request.getSubstanceText());
            item.setManufacturerId(request.getManufacturerId());
            item.setSupplyId(request.getSupplyId());
            item.setAllergenFlag(DeviceCatalogMapperImpl.persistBoolAsInt(request.getAllergenFlag()));
            item.setJurisdiction(request.getJurisdiction());
            item.setItemMinQuantity(request.getItemMinQuantity());
            item.setItemMaxQuantity(request.getItemMaxQuantity());
            item.setDescription(request.getDescription());

            item.setDoseUnitRatio(request.getDoseUnitRatio());
            item.setPurchaseUnitRatio(request.getPurchaseUnitRatio());
            item.setStockUnitRatio(request.getStockUnitRatio());
            item.setPackageUnitRatio(request.getPackageUnitRatio());
            // 追溯码贴码单位：耗材可选，由 AppService 成对校验后写入
            item.setTraceUnitCode(request.getTraceUnitCode());
            item.setTraceUnitRatio(request.getTraceUnitRatio());
        }
        cmd.setItems(Collections.singletonList(item));
        return cmd;
    }

    /**
     * 更新耗材请求转换为领域更新命令。
     *
     * <p>仅读取请求的 {@code purchaseUnitCode}/{@code splitUnitCode}，无 {@code unitCode}/{@code minUnitCode} 回落。</p>
     *
     * @param request 更新请求
     * @return 更新命令
     */
    @Override
    public UpdateDeviceDefinitionCommand toUpdateCommand(UpdateDeviceCatalogRequest request) {
        UpdateDeviceDefinitionCommand cmd = new UpdateDeviceDefinitionCommand();
        UpdateDeviceDefinitionCommand.Item item = new UpdateDeviceDefinitionCommand.Item();
        if (request != null) {
            item.setId(TextValueConverter.toNullableLong(request.getId()));
            String name = request.getName();
            if (name == null || name.isBlank()) {
                name = request.getMerchandiseName();
            }
            item.setName(name);
            item.setPyStr(request.getPyStr());
            item.setWbStr(request.getWbStr());
            item.setMerchandiseName(request.getMerchandiseName());
            item.setCategoryCode(request.getCategoryCode());
            item.setTypeCode(request.getTypeCode());
            // 领域命令字段名仍为 unitCode/minUnitCode，语义=采购/拆零
            item.setUnitCode(request.getPurchaseUnitCode());
            item.setTotalVolume(request.getTotalVolume());
            item.setMinUnitCode(request.getSplitUnitCode());
            item.setDefEncounterUnitCode(request.getDefEncounterUnitCode());
            item.setDefInhospitalUnitCode(request.getDefInhospitalUnitCode());
            item.setPackageUnitCode(request.getPackageUnitCode());
            item.setStockUnitCode(request.getStockUnitCode());
            item.setDoseUnitCode(request.getDoseUnitCode());
            item.setModelNumber(request.getModelNumber());
            item.setHvcmFlag(DeviceCatalogMapperImpl.persistBoolAsInt(request.getHvcmFlag()));
            item.setChargeableFlag(DeviceCatalogMapperImpl.persistBoolAsInt(request.getChargeableFlag()));
            item.setMrSettlementTypeCode(request.getMrSettlementTypeCode());
            item.setApprovalNumber(request.getApprovalNumber());
            item.setOrgId(request.getOrgId());
            item.setLocationId(request.getLocationId());
            item.setYbFlag(DeviceCatalogMapperImpl.persistBoolAsInt(request.getYbFlag()));
            item.setYbNo(request.getYbNo());
            item.setYbMatchFlag(DeviceCatalogMapperImpl.persistBoolAsInt(request.getYbMatchFlag()));
            item.setChrgitmLv(request.getChrgitmLv());
            item.setSubstanceText(request.getSubstanceText());
            item.setManufacturerId(request.getManufacturerId());
            item.setSupplyId(request.getSupplyId());
            item.setAllergenFlag(DeviceCatalogMapperImpl.persistBoolAsInt(request.getAllergenFlag()));
            item.setJurisdiction(request.getJurisdiction());
            item.setItemMinQuantity(request.getItemMinQuantity());
            item.setItemMaxQuantity(request.getItemMaxQuantity());
            item.setDescription(request.getDescription());

            item.setDoseUnitRatio(request.getDoseUnitRatio());
            item.setPurchaseUnitRatio(request.getPurchaseUnitRatio());
            item.setStockUnitRatio(request.getStockUnitRatio());
            item.setPackageUnitRatio(request.getPackageUnitRatio());
            // 追溯码贴码单位：编辑路径同样可选成对写入
            item.setTraceUnitCode(request.getTraceUnitCode());
            item.setTraceUnitRatio(request.getTraceUnitRatio());

            // 变更字段：与领域实体属性名 / FieldNameConstants 一致，供事件消费方与反射口径对齐
            List<String> changed = new ArrayList<>();
            if (request.getName() != null) {
                changed.add(FieldNameConstants.NAME);
            }
            if (request.getPyStr() != null) {
                changed.add(FieldNameConstants.PY_STR);
            }
            if (request.getWbStr() != null) {
                changed.add(FieldNameConstants.WB_STR);
            }
            if (request.getMerchandiseName() != null) {
                changed.add(FieldNameConstants.MERCHANDISE_NAME);
            }
            if (request.getCategoryCode() != null) {
                changed.add(FieldNameConstants.CATEGORY_CODE);
            }
            if (request.getTypeCode() != null) {
                changed.add(FieldNameConstants.TYPE_CODE);
            }
            if (request.getPurchaseUnitCode() != null) {
                changed.add(FieldNameConstants.PURCHASE_UNIT_CODE);
            }
            if (request.getTotalVolume() != null) {
                changed.add(FieldNameConstants.TOTAL_VOLUME);
            }
            if (request.getDefEncounterUnitCode() != null) {
                changed.add(FieldNameConstants.DEF_ENCOUNTER_UNIT_CODE);
            }
            if (request.getDefInhospitalUnitCode() != null) {
                changed.add(FieldNameConstants.DEF_INHOSPITAL_UNIT_CODE);
            }
            if (request.getSplitUnitCode() != null) {
                changed.add(FieldNameConstants.SPLIT_UNIT_CODE);
            }
            if (request.getPackageUnitCode() != null) {
                changed.add(FieldNameConstants.PACKAGE_UNIT_CODE);
            }
            if (request.getStockUnitCode() != null) {
                changed.add(FieldNameConstants.STOCK_UNIT_CODE);
            }
            if (request.getDoseUnitCode() != null) {
                changed.add(FieldNameConstants.DOSE_UNIT_CODE);
            }
            if (request.getModelNumber() != null) {
                changed.add("modelNumber");
            }
            if (request.getHvcmFlag() != null) {
                changed.add(FieldNameConstants.HVCM_FLAG);
            }
            if (request.getChargeableFlag() != null) {
                changed.add(FieldNameConstants.CHARGEABLE_FLAG);
            }
            if (request.getMrSettlementTypeCode() != null) {
                changed.add(FieldNameConstants.MR_SETTLEMENT_TYPE_CODE);
            }
            if (request.getApprovalNumber() != null) {
                changed.add("approvalNumber");
            }
            if (request.getOrgId() != null) {
                changed.add(FieldNameConstants.ORG_ID);
            }
            if (request.getLocationId() != null) {
                changed.add(FieldNameConstants.LOCATION_ID);
            }
            if (request.getYbFlag() != null) {
                changed.add(FieldNameConstants.YB_FLAG);
            }
            if (request.getYbNo() != null) {
                changed.add(FieldNameConstants.YB_NO);
            }
            if (request.getYbMatchFlag() != null) {
                changed.add(FieldNameConstants.YB_MATCH_FLAG);
            }
            if (request.getChrgitmLv() != null) {
                changed.add(FieldNameConstants.CHRGITM_LV);
            }
            if (request.getSubstanceText() != null) {
                changed.add("substanceText");
            }
            if (request.getManufacturerId() != null) {
                changed.add("manufacturerId");
            }
            if (request.getSupplyId() != null) {
                changed.add(FieldNameConstants.SUPPLY_ID);
            }
            if (request.getAllergenFlag() != null) {
                changed.add("allergenFlag");
            }
            if (request.getJurisdiction() != null) {
                changed.add("jurisdiction");
            }
            if (request.getItemMinQuantity() != null) {
                changed.add("itemMinQuantity");
            }
            if (request.getItemMaxQuantity() != null) {
                changed.add("itemMaxQuantity");
            }
            if (request.getDescription() != null) {
                changed.add("description");
            }
            if (request.getDoseUnitRatio() != null) {
                changed.add(FieldNameConstants.DOSE_UNIT_RATIO);
            }
            if (request.getPurchaseUnitRatio() != null) {
                changed.add(FieldNameConstants.PURCHASE_UNIT_RATIO);
            }
            if (request.getStockUnitRatio() != null) {
                changed.add(FieldNameConstants.STOCK_UNIT_RATIO);
            }
            if (request.getPackageUnitRatio() != null) {
                changed.add(FieldNameConstants.PACKAGE_UNIT_RATIO);
            }
            if (request.getTraceUnitCode() != null) {
                changed.add("traceUnitCode");
            }
            if (request.getTraceUnitRatio() != null) {
                changed.add("traceUnitRatio");
            }
            item.setChangedFields(changed);
        }
        cmd.setItems(Collections.singletonList(item));
        return cmd;
    }

    /**
     * 字符串主键列表转换为批量启用命令项列表。
     *
     * @param ids 主键字符串列表
     * @return 启用命令项列表
     */
    @Override
    public List<ActivateDeviceDefinitionsCommand.Item> toActivateItems(List<String> ids) {
        return BatchCommandItemAssembler.fromStringIds(
            ids,
            ActivateDeviceDefinitionsCommand.Item::new,
            ActivateDeviceDefinitionsCommand.Item::setId);
    }

    /**
     * 字符串主键列表转换为批量停用命令项列表。
     *
     * @param ids 主键字符串列表
     * @return 停用命令项列表
     */
    @Override
    public List<RetireDeviceDefinitionsCommand.Item> toRetireItems(List<String> ids) {
        return BatchCommandItemAssembler.fromStringIds(
            ids,
            RetireDeviceDefinitionsCommand.Item::new,
            RetireDeviceDefinitionsCommand.Item::setId);
    }

    /**
     * 字符串主键列表转换为批量删除命令项列表。
     *
     * @param ids 主键字符串列表
     * @return 删除命令项列表
     */
    @Override
    public List<DeleteDeviceDefinitionsCommand.Item> toDeleteItems(List<String> ids) {
        return BatchCommandItemAssembler.fromStringIds(
            ids,
            DeleteDeviceDefinitionsCommand.Item::new,
            DeleteDeviceDefinitionsCommand.Item::setId);
    }

    /**
     * 应用层布尔入参落库：领域命令仍为 0/1 整型。
     *
     * @param value 前端/契约布尔
     * @return {@code null}、0 或 1
     */
    private static Integer persistBoolAsInt(Boolean value) {
        if (value == null) {
            return null;
        }
        if (Boolean.TRUE.equals(value)) {
            return 1;
        }
        return 0;
    }

    /**
     * 高值耗材标志中文展示。
     *
     * @param hvcmFlag 是否高值耗材
     * @return 「是」「否」或未知不传 {@code null}
     */
    private static String hvcmCatalogText(Boolean hvcmFlag) {
        if (hvcmFlag == null) {
            return null;
        }
        if (Boolean.TRUE.equals(hvcmFlag)) {
            return "是";
        }
        return "否";
    }

}
