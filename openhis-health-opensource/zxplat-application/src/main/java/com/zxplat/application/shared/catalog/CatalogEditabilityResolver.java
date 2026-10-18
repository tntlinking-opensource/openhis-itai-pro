/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.catalog;

import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.constants.CatalogEditabilityConstants;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 三大目录编辑业务码读侧编排（跨聚合判定经各 DM {@code *QueryService}，B.6.4）。
 *
 * <p>诊疗套餐：开立仅落父项 {@code ServiceRequest}；子项编辑须继承父项开立锁（BR-CAT-014）。</p>
 */
@Component
@RequiredArgsConstructor
public class CatalogEditabilityResolver {

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final DeviceRequestQueryService deviceRequestQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final SupplyRequestQueryService supplyRequestQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

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
     * <p>判定顺序：① 本项存在有效 {@code ServiceRequest}；② 本项作为套餐子项时，
     * 任一未删父项存在有效 {@code ServiceRequest}（父开立≡子开立，BR-CAT-014）。</p>
     *
     * @param activityId 诊疗定义主键
     * @return 业务码与说明
     */
    public CatalogEditabilityQueryModel resolveActivityEditability(Long activityId) {
        CatalogEditabilityQueryModel model = new CatalogEditabilityQueryModel();
        if (activityId == null) {
            model.setEditabilityCode(CatalogEditabilityConstants.OK);
            model.setEditabilityMessage(CatalogEditabilityConstants.MSG_OK);
            return model;
        }
        // Gate：本项已被开立 → 临床锁（与历史口径一致）
        if (serviceRequestQueryService.existsByActivityId(activityId)) {
            model.setEditabilityCode(CatalogEditabilityConstants.LOCK_CLINICAL);
            model.setEditabilityMessage(CatalogEditabilityConstants.MSG_LOCK_CLINICAL_ACTIVITY);
            return model;
        }
        // Pre：反查父套餐；开立只写父 activity_id，子项无独立 SR
        List<Long> parentActivityIds =
            activityDefinitionQueryService.listParentActivityIdsByChildrenId(activityId);
        // Gate：任一父项已开立 → 子项同锁，禁止目录直改售价/结构
        if (serviceRequestQueryService.existsByActivityIdIn(parentActivityIds)) {
            model.setEditabilityCode(CatalogEditabilityConstants.LOCK_CLINICAL);
            model.setEditabilityMessage(CatalogEditabilityConstants.MSG_LOCK_CLINICAL_PACKAGE_CHILD);
            return model;
        }
        model.setEditabilityCode(CatalogEditabilityConstants.OK);
        model.setEditabilityMessage(CatalogEditabilityConstants.MSG_OK);
        return model;
    }

    /**
     * 产品目录可编辑性（药/材）。
     *
     * <p>与删除不同：编辑先判临床（二级）再判入库（一级）；删除仍用
     * {@link CatalogEditabilityConstants#isLocked}——任一引用即整单禁删。
     * 入库锁名称/分类/单位/拆零；开立另锁临床段（性质/剂型/剂量单位）；
     * 规格、拆分属性与医保等级仍可改。</p>
     *
     * @param itemId               目录项 id
     * @param hasClinicalReference 是否存在临床引用
     * @return 可编辑性查询模型
     */
    private CatalogEditabilityQueryModel resolveProductEditability(Long itemId, boolean hasClinicalReference) {
        CatalogEditabilityQueryModel model = new CatalogEditabilityQueryModel();
        if (itemId == null) {
            model.setEditabilityCode(CatalogEditabilityConstants.OK);
            model.setEditabilityMessage(CatalogEditabilityConstants.MSG_OK);
            return model;
        }
        // 二级：开过 → 结构 + 临床段锁定（规格/拆分属性/医保等级仍可改）
        if (hasClinicalReference) {
            model.setEditabilityCode(CatalogEditabilityConstants.LOCK_CLINICAL);
            model.setEditabilityMessage("医生开过该目录项，名称/分类/单位/拆零倍数/药品性质等字段不可修改");
            return model;
        }
        // 一级：未开过才校验入库（临床段与规格仍可改）
        boolean hasInbound = supplyRequestQueryService.existsInboundByItemId(itemId);
        if (hasInbound) {
            model.setEditabilityCode(CatalogEditabilityConstants.LOCK_INVENTORY);
            model.setEditabilityMessage("该目录已经入库过，名称/分类/单位/拆零倍数等字段不可修改");
            return model;
        }
        model.setEditabilityCode(CatalogEditabilityConstants.OK);
        model.setEditabilityMessage(CatalogEditabilityConstants.MSG_OK);
        return model;
    }
}
