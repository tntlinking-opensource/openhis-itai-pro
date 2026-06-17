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

package com.openhis.health.module.querysupport.order;

import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestFifoSignContext;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestFifoSignContext;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 医嘱签发 FIFO 发放上下文：跨聚合读编排（B.6.1.3），目录分类码由定义聚合补齐。
 */
@Component
@RequiredArgsConstructor
public class OrderRequestFifoSignContextLoader {

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final DeviceRequestQueryService deviceRequestQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;

    /**
     * 加载药品签发 FIFO 上下文并补齐目录分类码。
     *
     * @param medReqId 药品申请主键
     * @return 只读上下文
     */
    public MedicationRequestFifoSignContext loadMedicationFifoSignContextOrThrow(Long medReqId) {
        MedicationRequestFifoSignContext ctx = this.medicationRequestQueryService.loadFifoSignContextOrThrow(medReqId);
        if (ctx.getMedicationId() != null) {
            ctx.setCatalogCategoryCode(this.medicationDefinitionQueryService.resolveCategoryCodeByMedicationDefinitionId(
                ctx.getMedicationId()));
        }
        return ctx;
    }

    /**
     * 加载耗材签发 FIFO 上下文并补齐目录分类码。
     *
     * @param deviceReqId 耗材请求主键
     * @return 只读上下文
     */
    public DeviceRequestFifoSignContext loadDeviceFifoSignContextOrThrow(Long deviceReqId) {
        DeviceRequestFifoSignContext ctx = this.deviceRequestQueryService.loadFifoSignContextOrThrow(deviceReqId);
        if (ctx.getDeviceDefId() != null) {
            ctx.setCatalogCategoryCode(this.deviceDefinitionQueryService.resolveCategoryCodeByDeviceDefinitionId(
                ctx.getDeviceDefId()));
        }
        return ctx;
    }
}
