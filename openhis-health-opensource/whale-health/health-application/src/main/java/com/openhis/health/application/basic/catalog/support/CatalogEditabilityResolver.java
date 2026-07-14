/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.catalog.support;

import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.CatalogEditabilityConstants;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 三大目录编辑业务码读侧编排（跨聚合判定经各 DM {@code *QueryService}，B.6.4）。
 */
@Component
@RequiredArgsConstructor
public class CatalogEditabilityResolver {

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final DeviceRequestQueryService deviceRequestQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final SupplyRequestQueryService supplyRequestQueryService;

    /**
     * 解析药品目录编辑业务码。
     *
     * @param medicationId 药品定义主键
     * @return 业务码与说明
     */
    public CatalogEditabilityQueryModel resolveMedicationEditability(Long medicationId) {
        return this.resolveProductEditability(
            medicationId,
            medicationRequestQueryService.existsByMedicationId(medicationId));
    }

    /**
     * 解析器材目录编辑业务码。
     *
     * @param deviceDefId 器材定义主键
     * @return 业务码与说明
     */
    public CatalogEditabilityQueryModel resolveDeviceEditability(Long deviceDefId) {
        return this.resolveProductEditability(
            deviceDefId,
            deviceRequestQueryService.existsByDeviceDefId(deviceDefId));
    }

    /**
     * 解析诊疗目录编辑业务码（无库存侧码2）。
     *
     * @param activityId 诊疗定义主键
     * @return 业务码与说明
     */
    public CatalogEditabilityQueryModel resolveActivityEditability(Long activityId) {
        CatalogEditabilityQueryModel model = new CatalogEditabilityQueryModel();
        if (activityId == null) {
            model.setEditabilityCode(CatalogEditabilityConstants.OK);
            model.setEditabilityMessage("允许编辑");
            return model;
        }
        if (serviceRequestQueryService.existsByActivityId(activityId)) {
            model.setEditabilityCode(CatalogEditabilityConstants.LOCK_CLINICAL);
            model.setEditabilityMessage("医生开过该诊疗项目，分类/单位/价格等字段不可修改");
            return model;
        }
        model.setEditabilityCode(CatalogEditabilityConstants.OK);
        model.setEditabilityMessage("允许编辑");
        return model;
    }

    /**
     * 产品目录可编辑性：已入库锁分类/单位/价格；有临床引用锁关键字段。
     *
     * @param itemId               目录项 id
     * @param hasClinicalReference 是否存在临床引用
     * @return 可编辑性查询模型
     */
    private CatalogEditabilityQueryModel resolveProductEditability(Long itemId, boolean hasClinicalReference) {
        CatalogEditabilityQueryModel model = new CatalogEditabilityQueryModel();
        if (itemId == null) {
            model.setEditabilityCode(CatalogEditabilityConstants.OK);
            model.setEditabilityMessage("允许编辑");
            return model;
        }
        boolean hasInbound = supplyRequestQueryService.existsInboundByItemId(itemId);
        if (hasInbound) {
            model.setEditabilityCode(CatalogEditabilityConstants.LOCK_INVENTORY);
            model.setEditabilityMessage("该目录已经入库过，分类/单位/拆零倍数/价格等字段不可修改");
            return model;
        }
        if (hasClinicalReference) {
            model.setEditabilityCode(CatalogEditabilityConstants.LOCK_CLINICAL);
            model.setEditabilityMessage("医生开过该目录项，分类/单位/拆零倍数/价格等字段不可修改");
            return model;
        }
        model.setEditabilityCode(CatalogEditabilityConstants.OK);
        model.setEditabilityMessage("允许编辑");
        return model;
    }
}
