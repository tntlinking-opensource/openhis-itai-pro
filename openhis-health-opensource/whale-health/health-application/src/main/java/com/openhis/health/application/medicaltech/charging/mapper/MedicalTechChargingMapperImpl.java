/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.charging.mapper;

import com.openhis.health.application.medicaltech.charging.dto.MedicalTechDevicePricingWriteItemDto;
import com.openhis.health.application.medicaltech.charging.dto.MedicalTechPricedDeviceRowDto;
import com.openhis.health.application.medicaltech.charging.response.CancelMedicalTechDevicePricingResponse;
import com.openhis.health.application.medicaltech.charging.response.ConfirmMedicalTechDevicePricingResponse;
import com.openhis.health.application.medicaltech.charging.response.ListMedicalTechPricedDevicesResponse;
import com.openhis.health.application.medicaltech.charging.response.LoadMedicalTechChargingInitResponse;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.command.OffsetNursePricingChargeItemsByServiceAnchorsCommand;
import com.openhis.health.domain.share.constants.ChargeItemDmConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.workflow.devicedispense.command.GenerateDeviceDispenseFromTemporaryPathCommand;
import com.openhis.health.domain.workflow.devicerequest.command.CreateExecutedDeviceOrderCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * {@link MedicalTechChargingMapper} 实现：医技计费字段映射。
 *
 * <p>对应 IF-MT-CHG-01～05；null 安全返回空壳，禁止业务门闸。</p>
 */
@Component
@RequiredArgsConstructor
public class MedicalTechChargingMapperImpl implements MedicalTechChargingMapper {

    /**
     * 写行列表 → 创建已执行耗材请求命令（generateSource=medical-tech-pricing）。
     *
     * @param items       写行
     * @param encounterId 就诊
     * @param operatorId  操作人
     * @return 领域命令
     */
    @Override
    public CreateExecutedDeviceOrderCommand toCreateExecutedDeviceOrderCommand(
        List<MedicalTechDevicePricingWriteItemDto> items,
        Long encounterId,
        Long operatorId) {
        CreateExecutedDeviceOrderCommand command = new CreateExecutedDeviceOrderCommand();
        command.setOperatorId(operatorId);
        List<CreateExecutedDeviceOrderCommand.CreateExecutedDeviceOrderItemCommand> cmdItems = new ArrayList<>();
        if (items != null) {
            for (MedicalTechDevicePricingWriteItemDto row : items) {
                if (row == null) {
                    continue;
                }
                CreateExecutedDeviceOrderCommand.CreateExecutedDeviceOrderItemCommand item =
                    new CreateExecutedDeviceOrderCommand.CreateExecutedDeviceOrderItemCommand();
                // 医技划价独立来源码（与护士划价分列展示）
                item.setGenerateSourceEnum(GenerateSource.MEDICAL_TECH_PRICING.getCode());
                item.setDeviceDefId(row.getDefinitionId());
                item.setQuantity(row.getQuantity());
                item.setUnitCode(row.getUnitCode());
                item.setEncounterId(encounterId);
                item.setPatientId(row.getPatientId());
                item.setPerformLocation(row.getLocationId());
                cmdItems.add(item);
            }
        }
        command.setItems(cmdItems);
        return command;
    }

    /**
     * 临时路径生成耗材发放草稿命令。
     *
     * @param deviceReqId 耗材请求 id
     * @param row         写行
     * @param encounterId 就诊
     * @param operatorId  操作人
     * @return 命令
     */
    @Override
    public GenerateDeviceDispenseFromTemporaryPathCommand toGenerateDeviceDispenseFromTemporaryPathCommand(
        Long deviceReqId,
        MedicalTechDevicePricingWriteItemDto row,
        Long encounterId,
        Long operatorId) {
        GenerateDeviceDispenseFromTemporaryPathCommand command =
            new GenerateDeviceDispenseFromTemporaryPathCommand();
        command.setOperatorId(operatorId);
        GenerateDeviceDispenseFromTemporaryPathCommand.GenerateDeviceDispenseFromTemporaryPathItemCommand line =
            new GenerateDeviceDispenseFromTemporaryPathCommand.GenerateDeviceDispenseFromTemporaryPathItemCommand();
        line.setDeviceReqId(deviceReqId);
        if (row != null) {
            line.setDeviceDefId(row.getDefinitionId());
            line.setPatientId(row.getPatientId());
            line.setQuantity(row.getQuantity());
            line.setUnitCode(row.getUnitCode());
        }
        line.setEncounterId(encounterId);
        line.setLimitTime(LocalDateTime.now());
        command.setItems(List.of(line));
        return command;
    }

    /**
     * 医技划价挂账命令（PENDING_SETTLEMENT；requestingOrgId=loginTech；
     * generateSource=medical-tech-pricing）。
     *
     * @param row            写行
     * @param deviceReqId    耗材请求 id
     * @param dispenseId     主发放 id；可空
     * @param operatorId     操作人
     * @param loginTechOrgId 登录 TECH
     * @param encounterId    就诊
     * @return 收费命令
     */
    @Override
    public CreateChargeItemCommand toCreateChargeItemCommand(
        MedicalTechDevicePricingWriteItemDto row,
        Long deviceReqId,
        Long dispenseId,
        Long operatorId,
        Long loginTechOrgId,
        Long encounterId) {
        CreateChargeItemCommand command = new CreateChargeItemCommand();
        command.setOperatorId(operatorId);
        CreateChargeItemCommand.Item item = new CreateChargeItemCommand.Item();
        if (row != null) {
            item.setPatientId(row.getPatientId());
            item.setAccountId(row.getAccountId());
            item.setDefinitionId(row.getChargeItemDefinitionId());
            item.setProductId(row.getDefinitionId());
            item.setUnitPrice(row.getUnitPrice());
            BigDecimal qty = row.getQuantity();
            if (qty == null) {
                qty = BigDecimal.ONE;
            }
            item.setQuantityValue(qty);
            item.setQuantityUnit(
                StringUtils.defaultIfBlank(row.getUnitCode(), ChargeItemDmConstants.DEFAULT_QUANTITY_UNIT));
            item.setTotalPrice(row.getTotalPrice());
            item.setBaseAmount(row.getTotalPrice());
            item.setDiscountAmount(row.getTotalPrice());
        }
        item.setEncounterId(encounterId);
        item.setServiceTable(TableNameConstants.WKF_DEVICE_REQUEST);
        item.setServiceId(deviceReqId);
        item.setProductTable(TableNameConstants.ADM_DEVICE_DEFINITION);
        item.setSurchargeAmount(BigDecimal.ZERO);
        item.setOccurrenceTime(LocalDateTime.now());
        item.setDispenseTable(TableNameConstants.WKF_DEVICE_DISPENSE);
        item.setDispenseId(dispenseId);
        // 医技：org / requestingOrg 均落登录 TECH，满足费用清单条件③
        item.setOrgId(loginTechOrgId);
        item.setRequestingOrgId(loginTechOrgId);
        item.setGenerateSourceEnum(GenerateSource.MEDICAL_TECH_PRICING.getCode());
        command.setItems(List.of(item));
        return command;
    }

    /**
     * 按锚点冲销护士划价费用命令。
     *
     * @param encounterId 就诊
     * @param serviceId   DeviceRequest id
     * @param operatorId  操作人
     * @return 冲销命令
     */
    @Override
    public OffsetNursePricingChargeItemsByServiceAnchorsCommand toOffsetNursePricingChargeItemsByServiceAnchorsCommand(
        Long encounterId,
        Long serviceId,
        Long operatorId) {
        OffsetNursePricingChargeItemsByServiceAnchorsCommand command =
            new OffsetNursePricingChargeItemsByServiceAnchorsCommand();
        command.setOperatorId(operatorId);
        command.setEncounterId(encounterId);
        command.setServiceTable(TableNameConstants.WKF_DEVICE_REQUEST);
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (serviceId != null) {
            ids.add(serviceId);
        }
        command.setServiceIds(ids);
        return command;
    }

    /**
     * 取消划价库存回补命令（已发 Dispense revert 后累计量；划价未扣库时累加器为空）。
     *
     * @param operatorId       操作人
     * @param inventoryIdToQty 库存项 → 回补量
     * @return 增加库存命令
     */
    @Override
    public IncreaseQuantityCommand toIncreaseQuantityCommand(
        Long operatorId,
        Map<Long, BigDecimal> inventoryIdToQty) {
        IncreaseQuantityCommand command = new IncreaseQuantityCommand();
        command.setOperatorId(operatorId);
        List<IncreaseQuantityCommand.IncreaseQuantityItemCommand> items = new ArrayList<>();
        if (inventoryIdToQty != null) {
            for (Map.Entry<Long, BigDecimal> entry : inventoryIdToQty.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                // 忽略非正回补量，避免库存域断言失败
                if (entry.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                IncreaseQuantityCommand.IncreaseQuantityItemCommand item =
                    new IncreaseQuantityCommand.IncreaseQuantityItemCommand();
                item.setId(entry.getKey());
                item.setIncreaseQty(entry.getValue());
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * 划价成功响应。
     *
     * @param createdRequestIds 新建请求 id
     * @return 响应
     */
    @Override
    public ConfirmMedicalTechDevicePricingResponse toConfirmResponse(List<Long> createdRequestIds) {
        ConfirmMedicalTechDevicePricingResponse response = new ConfirmMedicalTechDevicePricingResponse();
        response.setSucceeded(Boolean.TRUE);
        response.setMessage("医技耗材划价成功");
        if (createdRequestIds == null) {
            response.setCreatedRequestIds(new ArrayList<>());
        } else {
            response.setCreatedRequestIds(createdRequestIds);
        }
        return response;
    }

    /**
     * 已划价列表响应。
     *
     * @param rows 行
     * @return 响应
     */
    @Override
    public ListMedicalTechPricedDevicesResponse toPricedListResponse(List<MedicalTechPricedDeviceRowDto> rows) {
        ListMedicalTechPricedDevicesResponse response = new ListMedicalTechPricedDevicesResponse();
        if (rows == null) {
            response.setRows(new ArrayList<>());
        } else {
            response.setRows(rows);
        }
        return response;
    }

    /**
     * 取消划价响应。
     *
     * @param cancelledCount 冲销条数
     * @return 响应
     */
    @Override
    public CancelMedicalTechDevicePricingResponse toCancelResponse(int cancelledCount) {
        CancelMedicalTechDevicePricingResponse response = new CancelMedicalTechDevicePricingResponse();
        response.setSucceeded(Boolean.TRUE);
        response.setCancelledCount(Integer.valueOf(cancelledCount));
        response.setMessage("取消医技耗材划价成功");
        return response;
    }

    /**
     * 初始化响应。
     *
     * @param encounterId       就诊
     * @param defaultLocationId 默认房
     * @param locationOptions   选项
     * @return 响应
     */
    @Override
    public LoadMedicalTechChargingInitResponse toInitResponse(
        Long encounterId,
        Long defaultLocationId,
        List<OptionDto> locationOptions) {
        LoadMedicalTechChargingInitResponse response = new LoadMedicalTechChargingInitResponse();
        response.setEncounterId(encounterId);
        response.setDefaultLocationId(defaultLocationId);
        if (locationOptions == null) {
            response.setLocationOptions(new ArrayList<>());
        } else {
            response.setLocationOptions(locationOptions);
        }
        return response;
    }
}
