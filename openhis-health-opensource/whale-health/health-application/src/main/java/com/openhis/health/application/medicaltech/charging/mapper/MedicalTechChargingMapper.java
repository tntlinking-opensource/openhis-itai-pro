/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.charging.mapper;

import com.openhis.health.application.medicaltech.charging.dto.MedicalTechDevicePricingWriteItemDto;
import com.openhis.health.application.medicaltech.charging.response.CancelMedicalTechDevicePricingResponse;
import com.openhis.health.application.medicaltech.charging.response.ConfirmMedicalTechDevicePricingResponse;
import com.openhis.health.application.medicaltech.charging.response.ListMedicalTechPricedDevicesResponse;
import com.openhis.health.application.medicaltech.charging.response.LoadMedicalTechChargingInitResponse;
import com.openhis.health.application.medicaltech.charging.dto.MedicalTechPricedDeviceRowDto;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.command.OffsetNursePricingChargeItemsByServiceAnchorsCommand;
import com.openhis.health.domain.workflow.devicedispense.command.GenerateDeviceDispenseFromTemporaryPathCommand;
import com.openhis.health.domain.workflow.devicerequest.command.CreateExecutedDeviceOrderCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.whale.core.application.dto.OptionDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 医技计费应用层装配：写行 → Domain Command；Response 壳。
 *
 * <p>禁止承载库房归属 / TECH 门闸；业务判定在 AppService。</p>
 */
public interface MedicalTechChargingMapper {

    /**
     * 写行列表 → 创建已执行耗材请求命令（generateSource=medical-tech-pricing）。
     *
     * @param items       写行
     * @param encounterId 就诊
     * @param operatorId  操作人
     * @return 领域命令
     */
    CreateExecutedDeviceOrderCommand toCreateExecutedDeviceOrderCommand(
        List<MedicalTechDevicePricingWriteItemDto> items,
        Long encounterId,
        Long operatorId);

    /**
     * 临时路径生成耗材发放草稿命令。
     *
     * @param deviceReqId 耗材请求 id
     * @param row         写行
     * @param encounterId 就诊
     * @param operatorId  操作人
     * @return 命令
     */
    GenerateDeviceDispenseFromTemporaryPathCommand toGenerateDeviceDispenseFromTemporaryPathCommand(
        Long deviceReqId,
        MedicalTechDevicePricingWriteItemDto row,
        Long encounterId,
        Long operatorId);

    /**
     * 医技划价挂账命令（PENDING_SETTLEMENT；requestingOrgId=loginTech；
     * generateSource=medical-tech-pricing）。
     *
     * @param row            写行
     * @param deviceReqId    耗材请求 id
     * @param dispenseId     主发放 id；可空
     * @param operatorId     操作人
     * @param loginTechOrgId 登录 TECH（orgId + requestingOrgId）
     * @param encounterId    就诊
     * @return 收费命令
     */
    CreateChargeItemCommand toCreateChargeItemCommand(
        MedicalTechDevicePricingWriteItemDto row,
        Long deviceReqId,
        Long dispenseId,
        Long operatorId,
        Long loginTechOrgId,
        Long encounterId);

    /**
     * 按锚点冲销医技划价费用命令（域方法兼容护士/医技划价来源）。
     *
     * @param encounterId 就诊
     * @param serviceId   DeviceRequest id
     * @param operatorId  操作人
     * @return 冲销命令
     */
    OffsetNursePricingChargeItemsByServiceAnchorsCommand toOffsetNursePricingChargeItemsByServiceAnchorsCommand(
        Long encounterId,
        Long serviceId,
        Long operatorId);

    /**
     * 取消划价时若发放已扣库，按累加器构造库存回补命令。
     *
     * @param operatorId       操作人
     * @param inventoryIdToQty 库存项 → 回补量（可空）
     * @return 增加库存命令；无正量项时 items 为空
     */
    IncreaseQuantityCommand toIncreaseQuantityCommand(
        Long operatorId,
        Map<Long, BigDecimal> inventoryIdToQty);

    /**
     * 划价成功响应。
     *
     * @param createdRequestIds 新建请求 id
     * @return 响应
     */
    ConfirmMedicalTechDevicePricingResponse toConfirmResponse(List<Long> createdRequestIds);

    /**
     * 已划价列表响应。
     *
     * @param rows 行
     * @return 响应
     */
    ListMedicalTechPricedDevicesResponse toPricedListResponse(List<MedicalTechPricedDeviceRowDto> rows);

    /**
     * 取消划价响应。
     *
     * @param cancelledCount 冲销条数
     * @return 响应
     */
    CancelMedicalTechDevicePricingResponse toCancelResponse(int cancelledCount);

    /**
     * 初始化响应。
     *
     * @param encounterId       就诊
     * @param defaultLocationId 默认房
     * @param locationOptions   选项
     * @return 响应
     */
    LoadMedicalTechChargingInitResponse toInitResponse(
        Long encounterId,
        Long defaultLocationId,
        List<OptionDto> locationOptions);
}
