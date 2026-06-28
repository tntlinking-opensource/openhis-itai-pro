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

package com.openhis.health.module.querysupport.priceadjustment;

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
import com.openhis.health.module.querysupport.priceadjustment.dto.PriceAdjustmentInTransitCheckDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 物流调价提交前在途互斥预检（BR-PRC-SUB-003）：跨聚合只读，查药品/耗材医嘱与发放非终态行。
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
     * 按调价单号检查在途互斥。
     *
     * @param busNo 调价单号
     * @return 首条阻断结果；无阻断时 {@code blocked=false}
     */
    public PriceAdjustmentInTransitCheckDto checkByBusNo(String busNo) {
        PriceAdjustmentInTransitCheckDto pass = new PriceAdjustmentInTransitCheckDto();
        pass.setBlocked(false);
        // Pre：空单号视为通过，不阻断提交。
        if (StringUtils.isBlank(busNo)) {
            return pass;
        }
        PriceAdjustmentDocumentQueryModel document =
            this.priceAdjustmentQueryService.getDocumentByBusNo(busNo.trim());
        if (document.getLines() == null) {
            return pass;
        }
        // Gate：逐项目行检查；首条阻断即返回。
        for (PriceAdjustmentLineQueryModel line : document.getLines()) {
            PriceAdjustmentInTransitCheckDto blocked = this.checkLine(line);
            if (blocked.isBlocked()) {
                return blocked;
            }
        }
        return pass;
    }

    /**
     * 检查单条项目行在途状态。
     *
     * @param line 调价项目行读模型
     * @return 阻断结果
     */
    public PriceAdjustmentInTransitCheckDto checkLine(PriceAdjustmentLineQueryModel line) {
        PriceAdjustmentInTransitCheckDto pass = new PriceAdjustmentInTransitCheckDto();
        pass.setBlocked(false);
        if (line == null || line.getItemId() == null || StringUtils.isBlank(line.getItemTable())) {
            return pass;
        }
        // 非物流项目表不参与在途互斥（诊疗仅 pending 互斥）。
        if (!PriceAdjustmentItemTableSupport.isLogisticsItemTable(line.getItemTable())) {
            return pass;
        }
        String itemTable = line.getItemTable().trim();
        Long itemId = line.getItemId();
        // 业务分支：药品定义表 → 查药品 Request/Dispense 非终态。
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(itemTable)) {
            if (this.medicationRequestQueryService.existsInTransitRequestByMedicationId(itemId)) {
                return this.blocked(
                    CustomConstants.PriceAdjustment.IN_TRANSIT_REASON_MED_REQUEST,
                    "存在在途药品医嘱申请，无法提交调价",
                    itemTable,
                    itemId
                );
            }
            if (this.medicationDispenseQueryService.existsInTransitDispenseByMedicationId(itemId)) {
                return this.blocked(
                    CustomConstants.PriceAdjustment.IN_TRANSIT_REASON_MED_DISPENSE,
                    "存在在途药品发放，无法提交调价",
                    itemTable,
                    itemId
                );
            }
            return pass;
        }
        // 业务分支：耗材定义表 → 查耗材 Request/Dispense 非终态。
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(itemTable)) {
            if (this.deviceRequestQueryService.existsInTransitRequestByDeviceDefId(itemId)) {
                return this.blocked(
                    CustomConstants.PriceAdjustment.IN_TRANSIT_REASON_DEVICE_REQUEST,
                    "存在在途耗材申请，无法提交调价",
                    itemTable,
                    itemId
                );
            }
            if (this.deviceDispenseQueryService.existsInTransitDispenseByDeviceDefId(itemId)) {
                return this.blocked(
                    CustomConstants.PriceAdjustment.IN_TRANSIT_REASON_DEVICE_DISPENSE,
                    "存在在途耗材发放，无法提交调价",
                    itemTable,
                    itemId
                );
            }
        }
        return pass;
    }

    private PriceAdjustmentInTransitCheckDto blocked(
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
