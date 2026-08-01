/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.observation.service;

import com.openhis.health.domain.clinical.observation.entity.Observation;
import com.openhis.health.domain.clinical.observation.queries.model.ObservationModel;
import com.openhis.health.domain.clinical.observation.repository.ObservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 临床观察实例读侧查询服务（DM-CLN-007 §5.2）。
 *
 * <p>仅本聚合 {@link ObservationRepository}；definitionDisplay/categoryCode 由 AppService enrich（B.6.4）。</p>
 */
@Service
@RequiredArgsConstructor
public class ObservationQueryService {

    private final ObservationRepository repository;

    /**
     * 按就诊列出观察实例。
     *
     * @param encounterId 就诊主键
     * @return 读模型列表
     */
    public List<ObservationModel> listByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return new ArrayList<>();
        }
        List<ObservationModel> models = new ArrayList<>();
        for (Observation row : repository.findByEncounterIdAndIsDeletedFalse(encounterId)) {
            models.add(toModel(row));
        }
        models.sort(Comparator.comparing(ObservationModel::getId, Comparator.nullsLast(Long::compareTo)));
        return models;
    }

    /**
     * 按服务申请列出观察实例。
     *
     * @param serviceRequestId 服务申请主键
     * @return 读模型列表
     */
    public List<ObservationModel> listByServiceRequestId(Long serviceRequestId) {
        if (serviceRequestId == null) {
            return new ArrayList<>();
        }
        List<ObservationModel> models = new ArrayList<>();
        for (Observation row : repository.findByServiceRequestIdAndIsDeletedFalse(serviceRequestId)) {
            models.add(toModel(row));
        }
        models.sort(Comparator.comparing(ObservationModel::getId, Comparator.nullsLast(Long::compareTo)));
        return models;
    }

    /**
     * 按 SR+definition 幂等键读取。
     *
     * @param srId  服务申请主键
     * @param defId 观察定义主键
     * @return 读模型
     */
    public Optional<ObservationModel> findBySrAndDefinition(Long srId, Long defId) {
        if (srId == null || defId == null) {
            return Optional.empty();
        }
        return repository.findByServiceRequestIdAndDefinitionIdAndIsDeletedFalse(srId, defId)
            .map(this::toModel);
    }

    private ObservationModel toModel(Observation entity) {
        ObservationModel model = new ObservationModel();
        model.setId(entity.getId());
        model.setEncounterId(entity.getEncounterId());
        model.setServiceRequestId(entity.getServiceRequestId());
        model.setDefinitionId(entity.getDefinitionId());
        model.setValueQuantity(entity.getValueQuantity());
        model.setValueString(entity.getValueString());
        model.setInterpretationCode(entity.getInterpretationCode());
        model.setStatusEnum(entity.getStatusEnum());
        model.setSourceObservationId(entity.getSourceObservationId());
        model.setSourceModuleEnum(entity.getSourceModuleEnum());
        return model;
    }
}
