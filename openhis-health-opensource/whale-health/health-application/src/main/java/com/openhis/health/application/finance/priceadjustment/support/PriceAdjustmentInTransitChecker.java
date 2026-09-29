/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.support;

import com.openhis.health.application.finance.priceadjustment.support.dto.PriceAdjustmentInTransitCheckDto;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentDocumentQueryModel;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentLineQueryModel;
import com.openhis.health.domain.custom.priceadjustment.service.PriceAdjustmentQueryService;
import com.openhis.health.domain.custom.priceadjustment.support.PriceAdjustmentItemTableSupport;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 物流调价提交前硬在途互斥预检（BR-PRC-SUB-003）：已签发申请 / 在途发放。
 *
 * <p>未签发 DRAFT 由 {@link PriceAdjustmentUnsignedDraftPolicySupport} 按 48h 窗口单独处理，不在此合并。</p>
 */
@Component
@RequiredArgsConstructor
public class PriceAdjustmentInTransitChecker {

    private final PriceAdjustmentQueryService priceAdjustmentQueryService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final DeviceRequestQueryService deviceRequestQueryService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /**
     * 按调价单号检查硬在途互斥。
     *
     * @param busNo 调价单号
     * @return 首条阻断结果；无阻断时 {@code blocked=false}
     */
    public PriceAdjustmentInTransitCheckDto checkByBusNo(String busNo) {
        return this.checkHardByBusNo(busNo);
    }

    /**
     * 仅查硬在途（已签发申请 + 在途发放；不含未签发 DRAFT）。
     *
     * @param busNo 调价单号
     * @return 硬阻断结果；无则 {@code blocked=false}
     */
    public PriceAdjustmentInTransitCheckDto checkHardByBusNo(String busNo) {
        PriceAdjustmentInTransitCheckDto pass = this.passResult();
        // Pre：空单号视为通过，不阻断提交
        if (StringUtils.isEmpty(busNo)) {
            return pass;
        }
        PriceAdjustmentDocumentQueryModel document =
            this.priceAdjustmentQueryService.getDocumentByBusNo(busNo.trim());
        if (document.getLines() == null) {
            return pass;
        }
        // Gate：逐项目行硬在途；首条阻断即返回
        for (PriceAdjustmentLineQueryModel line : document.getLines()) {
            PriceAdjustmentInTransitCheckDto blocked = this.checkHardLine(line);
            if (blocked.isBlocked()) {
                return blocked;
            }
        }
        return pass;
    }

    /**
     * 检查单条项目行硬在途状态。
     *
     * @param line 调价项目行读模型
     * @return 阻断结果
     */
    public PriceAdjustmentInTransitCheckDto checkLine(PriceAdjustmentLineQueryModel line) {
        return this.checkHardLine(line);
    }

    /**
     * 单行硬在途：已签发申请或在途发放。
     *
     * @param line 调价项目行
     * @return 硬阻断或通过
     */
    private PriceAdjustmentInTransitCheckDto checkHardLine(PriceAdjustmentLineQueryModel line) {
        PriceAdjustmentInTransitCheckDto pass = this.passResult();
        // 业务分支：空行跳过
        if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
            return pass;
        }
        // 非物流项目表不参与在途互斥（诊疗仅 pending 互斥）
        if (!PriceAdjustmentItemTableSupport.isLogisticsItemTable(line.getItemTable())) {
            return pass;
        }
        String itemTable = line.getItemTable().trim();
        Long itemId = line.getItemId();
        // 业务分支：药品定义 → 硬在途申请 / 在途发放（B.6.4）
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(itemTable)) {
            if (this.medicationRequestQueryService.existsHardInTransitRequestByMedicationId(itemId)) {
                return this.hardBlocked(
                    CustomConstants.PriceAdjustment.IN_TRANSIT_REASON_MED_REQUEST,
                    "存在在途药品医嘱申请，无法提交调价",
                    itemTable,
                    itemId
                );
            }
            if (this.medicationDispenseQueryService.existsInTransitDispenseByMedicationId(itemId)) {
                return this.hardBlocked(
                    CustomConstants.PriceAdjustment.IN_TRANSIT_REASON_MED_DISPENSE,
                    "存在在途药品发放，无法提交调价",
                    itemTable,
                    itemId
                );
            }
            return pass;
        }
        // 业务分支：耗材定义 → 硬在途申请 / 在途发放（B.6.4）
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(itemTable)) {
            if (this.deviceRequestQueryService.existsHardInTransitRequestByDeviceDefId(itemId)) {
                return this.hardBlocked(
                    CustomConstants.PriceAdjustment.IN_TRANSIT_REASON_DEVICE_REQUEST,
                    "存在在途耗材申请，无法提交调价",
                    itemTable,
                    itemId
                );
            }
            if (this.deviceDispenseQueryService.existsInTransitDispenseByDeviceDefId(itemId)) {
                return this.hardBlocked(
                    CustomConstants.PriceAdjustment.IN_TRANSIT_REASON_DEVICE_DISPENSE,
                    "存在在途耗材发放，无法提交调价",
                    itemTable,
                    itemId
                );
            }
        }
        return pass;
    }

    /**
     * 构造通过结果。
     *
     * @return blocked=false
     */
    private PriceAdjustmentInTransitCheckDto passResult() {
        PriceAdjustmentInTransitCheckDto pass = new PriceAdjustmentInTransitCheckDto();
        pass.setBlocked(false);
        return pass;
    }

    /**
     * 构造硬阻断结果。
     *
     * @param reasonCode    阻断原因码
     * @param reasonMessage 阻断说明
     * @param itemTable     物品表
     * @param itemId        物品 id
     * @return blocked=true
     */
    private PriceAdjustmentInTransitCheckDto hardBlocked(
        String reasonCode,
        String reasonMessage,
        String itemTable,
        Long itemId
    ) {
        PriceAdjustmentInTransitCheckDto dto = new PriceAdjustmentInTransitCheckDto();
        dto.setBlocked(true);
        dto.setReasonCode(reasonCode);
        dto.setReasonMessage(reasonMessage);
        dto.setItemTable(itemTable);
        dto.setItemId(itemId);
        return dto;
    }
}
