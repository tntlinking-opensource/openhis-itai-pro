/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.support;

import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestFifoSignContext;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestFifoSignContext;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 医嘱签发 FIFO 发放上下文：跨聚合读编排（B.6.1.3），目录分类码由定义聚合补齐。
 *
 * <p>单条与批量装载共用同一字段切片与分类补齐口径，批量仅减少 DB round-trip，不改变签发语义。</p>
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
     * 批量加载药品签发 FIFO 上下文并补齐目录分类码（与单条 {@link #loadMedicationFifoSignContextOrThrow} 同口径）。
     *
     * @param medReqIds 药品申请主键集合；空则返回空 Map
     * @return 申请主键 → FIFO 上下文
     */
    public Map<Long, MedicationRequestFifoSignContext> loadMedicationFifoSignContextsOrThrow(Collection<Long> medReqIds) {
        Map<Long, MedicationRequestFifoSignContext> contexts =
            this.medicationRequestQueryService.mapFifoSignContextsByIdsOrThrow(medReqIds);
        if (contexts.isEmpty()) {
            return contexts;
        }
        // Pre：收集本批药品定义，一次查分类码
        Set<Long> medicationDefinitionIds = new LinkedHashSet<>();
        for (MedicationRequestFifoSignContext ctx : contexts.values()) {
            if (ctx == null || ctx.getMedicationId() == null) {
                continue;
            }
            medicationDefinitionIds.add(ctx.getMedicationId());
        }
        Map<Long, String> categoryByMedicationId = this.mapMedicationCategoryCodeByDefinitionIds(medicationDefinitionIds);
        for (MedicationRequestFifoSignContext ctx : contexts.values()) {
            if (ctx == null || ctx.getMedicationId() == null) {
                continue;
            }
            ctx.setCatalogCategoryCode(categoryByMedicationId.get(ctx.getMedicationId()));
        }
        return contexts;
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

    /**
     * 按药品定义主键批量解析 categoryCode（与单条 resolve 同字段）。
     *
     * @param medicationDefinitionIds 药品定义主键
     * @return 定义主键 → categoryCode；未命中不进 Map
     */
    private Map<Long, String> mapMedicationCategoryCodeByDefinitionIds(Collection<Long> medicationDefinitionIds) {
        Map<Long, String> categoryById = new HashMap<>();
        if (medicationDefinitionIds == null || medicationDefinitionIds.isEmpty()) {
            return categoryById;
        }
        List<MedicationDefinition> definitions = this.medicationDefinitionQueryService.findAllByIds(medicationDefinitionIds);
        for (MedicationDefinition definition : definitions) {
            if (definition == null || definition.getId() == null) {
                continue;
            }
            categoryById.put(definition.getId(), definition.getCategoryCode());
        }
        return categoryById;
    }
}
