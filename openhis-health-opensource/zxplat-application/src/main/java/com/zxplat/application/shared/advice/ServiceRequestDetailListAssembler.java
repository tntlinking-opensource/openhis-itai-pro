/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.advice;

import com.zxplat.domain.administration.specimendefinition.queries.model.SpecimenDefinitionBatchItemQueryModel;
import com.zxplat.domain.administration.specimendefinition.service.SpecimenDefinitionQueryService;
import com.zxplat.domain.diagnosis.bodystructure.service.BodyStructureQueryService;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.workflow.servicerequest.entity.ServiceRequestDetail;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestDetailListModel;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务申请明细列表展示装配（部位/标本展示名 enrich；B.6.4 多聚合只读编排）。
 */
@Component
@RequiredArgsConstructor
public class ServiceRequestDetailListAssembler {

    /** 诊疗申请明细只读查询。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    /** 身体部位定义只读查询（明细 target 展示名）。 */
    private final BodyStructureQueryService bodyStructureQueryService;
    /** 样本定义只读查询（明细 target 展示名）。 */
    private final SpecimenDefinitionQueryService specimenDefinitionQueryService;

    /**
     * 按服务申请主键批量加载明细行，并解析部位/样本展示名（DM-WKF-004 §6.2.24）。
     *
     * @param serviceRequestIds 服务申请主键集合
     * @return 服务申请主键 → 明细行列表
     */
    public Map<Long, List<ServiceRequestDetailListModel>> mapDetailListModelsByServiceRequestIds(
        Collection<Long> serviceRequestIds) {
        List<ServiceRequestDetail> detailRows =
            this.serviceRequestQueryService.findActiveDetailsByServiceRequestIds(serviceRequestIds);
        if (detailRows.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> bodyStructureIds = new ArrayList<>();
        List<Long> specimenDefinitionIds = new ArrayList<>();
        for (ServiceRequestDetail detail : detailRows) {
            if (detail == null || Boolean.TRUE.equals(detail.getIsDeleted())) {
                continue;
            }
            if (TableNameConstants.DIA_BODY_STRUCTURE.equals(detail.getTargetTable()) && detail.getTargetId() != null) {
                bodyStructureIds.add(detail.getTargetId());
            } else if (TableNameConstants.ADM_SPECIMEN_DEFINITION.equals(detail.getTargetTable())
                && detail.getTargetId() != null) {
                specimenDefinitionIds.add(detail.getTargetId());
            }
        }
        Map<Long, String> bodyStructureNameMap = this.bodyStructureQueryService.mapNamesByIds(bodyStructureIds);
        Map<Long, String> specimenNameMap = this.mapSpecimenNamesByIds(specimenDefinitionIds);
        Map<Long, List<ServiceRequestDetailListModel>> grouped = new HashMap<>();
        for (ServiceRequestDetail detail : detailRows) {
            if (detail == null || detail.getServiceReqId() == null || Boolean.TRUE.equals(detail.getIsDeleted())) {
                continue;
            }
            ServiceRequestDetailListModel model = new ServiceRequestDetailListModel();
            model.setId(detail.getId());
            model.setServiceReqId(detail.getServiceReqId());
            model.setTargetTable(detail.getTargetTable());
            model.setTargetId(detail.getTargetId());
            model.setRemark(detail.getRemark());
            model.setTargetDisplayName(this.resolveDetailTargetDisplayName(
                detail.getTargetTable(), detail.getTargetId(), bodyStructureNameMap, specimenNameMap));
            grouped.computeIfAbsent(detail.getServiceReqId(), key -> new ArrayList<>()).add(model);
        }
        return grouped;
    }

    /**
     * 样本定义 id 批量 → 名称映射。
     *
     * @param specimenDefinitionIds id 列表
     * @return id → name
     */
    private Map<Long, String> mapSpecimenNamesByIds(List<Long> specimenDefinitionIds) {
        Map<Long, String> result = new HashMap<>();
        if (specimenDefinitionIds == null || specimenDefinitionIds.isEmpty()) {
            return result;
        }
        List<SpecimenDefinitionBatchItemQueryModel> rows =
            this.specimenDefinitionQueryService.listByIds(specimenDefinitionIds);
        for (SpecimenDefinitionBatchItemQueryModel row : rows) {
            if (row != null && row.getId() != null) {
                result.put(row.getId(), row.getName());
            }
        }
        return result;
    }

    /**
     * 诊疗明细 target 展示名：按 targetTable 从 bodyStructure/specimen 映射取值。
     *
     * @param targetTable           目标表
     * @param targetId              目标 id
     * @param bodyStructureNameMap  部位名称映射
     * @param specimenNameMap       样本名称映射
     * @return 展示名；无法解析时 null
     */
    private String resolveDetailTargetDisplayName(
        String targetTable,
        Long targetId,
        Map<Long, String> bodyStructureNameMap,
        Map<Long, String> specimenNameMap) {
        if (targetId == null || targetTable == null) {
            return null;
        }
        if (TableNameConstants.DIA_BODY_STRUCTURE.equals(targetTable)) {
            return bodyStructureNameMap.get(targetId);
        }
        if (TableNameConstants.ADM_SPECIMEN_DEFINITION.equals(targetTable)) {
            return specimenNameMap.get(targetId);
        }
        return null;
    }
}
