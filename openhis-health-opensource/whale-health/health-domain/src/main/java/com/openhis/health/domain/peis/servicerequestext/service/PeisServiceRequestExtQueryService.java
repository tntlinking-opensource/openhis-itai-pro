/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.servicerequestext.service;

import com.openhis.health.domain.peis.servicerequestext.entity.PeisServiceRequestExt;
import com.openhis.health.domain.peis.servicerequestext.queries.model.PeisServiceRequestExtModel;
import com.openhis.health.domain.peis.servicerequestext.repository.PeisServiceRequestExtRepository;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.health.domain.workflow.servicerequest.repository.ServiceRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PEIS 服务申请执行扩展读侧查询服务（DM-PEIS-001）。
 */
@Service
@RequiredArgsConstructor
public class PeisServiceRequestExtQueryService {

    private final PeisServiceRequestExtRepository repository;
    private final ServiceRequestRepository serviceRequestRepository;

    /**
     * 按服务申请主键查询执行扩展（含 SR 的 encounterId/activityId）。
     *
     * @param serviceRequestId 服务申请主键
     * @return 读模型；不存在返回 null
     */
    public PeisServiceRequestExtModel findByServiceRequestId(Long serviceRequestId) {
        // join SR → 软删过滤 → 组装 Model
        if (serviceRequestId == null) {
            return null;
        }
        Optional<PeisServiceRequestExt> extOpt =
            repository.findByServiceRequestIdAndIsDeletedFalse(serviceRequestId);
        if (extOpt.isEmpty()) {
            return null;
        }
        ServiceRequest sr = serviceRequestRepository.findById(serviceRequestId).orElse(null);
        return toModel(extOpt.get(), sr);
    }

    /**
     * 按就诊列出执行扩展（先取 SR id，再加载 ext）。
     *
     * @param encounterId 就诊主键
     * @return 读模型列表
     */
    public List<PeisServiceRequestExtModel> listByEncounterId(Long encounterId) {
        // join SR → 软删过滤 → 组装 Model
        if (encounterId == null) {
            return new ArrayList<>();
        }
        List<ServiceRequest> srs = serviceRequestRepository.findByEncounterId(encounterId);
        if (srs.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, ServiceRequest> srById = new HashMap<>();
        List<Long> srIds = new ArrayList<>();
        for (ServiceRequest sr : srs) {
            if (sr.getId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(sr.getIsDeleted())) {
                continue;
            }
            if (RequestStatus.CANCELLED.getCode().equals(sr.getStatusEnum())) {
                continue;
            }
            srIds.add(sr.getId());
            srById.put(sr.getId(), sr);
        }
        if (srIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<PeisServiceRequestExt> rows =
            repository.findByServiceRequestIdInAndIsDeletedFalse(srIds);
        List<PeisServiceRequestExtModel> models = new ArrayList<>();
        for (PeisServiceRequestExt row : rows) {
            models.add(toModel(row, srById.get(row.getServiceRequestId())));
        }
        return models;
    }

    private PeisServiceRequestExtModel toModel(PeisServiceRequestExt entity, ServiceRequest sr) {
        PeisServiceRequestExtModel model = new PeisServiceRequestExtModel();
        model.setServiceRequestId(entity.getServiceRequestId());
        model.setExecStatusEnum(entity.getExecStatusEnum());
        model.setLisDispatchEnum(entity.getLisDispatchEnum());
        model.setPacsDispatchEnum(entity.getPacsDispatchEnum());
        if (sr != null) {
            model.setEncounterId(sr.getEncounterId());
            model.setActivityId(sr.getActivityId());
        }
        return model;
    }
}
