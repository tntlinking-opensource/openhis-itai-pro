/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.support;

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
