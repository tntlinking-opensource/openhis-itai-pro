/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.encounterext.service;

import com.openhis.health.domain.peis.encounterext.entity.PeisEncounterExt;
import com.openhis.health.domain.peis.encounterext.queries.model.PeisEncounterExtModel;
import com.openhis.health.domain.peis.encounterext.repository.PeisEncounterExtRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PEIS 就诊扩展读侧查询服务（DM-PEIS-007）。
 */
@Service
@RequiredArgsConstructor
public class PeisEncounterExtQueryService {

    private final PeisEncounterExtRepository repository;

    /**
     * 按就诊主键获取扩展（不存在则抛错）。
     *
     * @param encounterId 就诊主键
     * @return 读模型
     */
    public PeisEncounterExtModel getByEncounterId(Long encounterId) {
        if (encounterId == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_ENC_EXT_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        PeisEncounterExt entity = repository.findByEncounterIdAndIsDeletedFalse(encounterId)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PEIS_ENC_EXT_NOT_FOUND, "就诊扩展不存在"));
        return toModel(entity);
    }

    /**
     * 按就诊主键可选加载扩展。
     */
    public java.util.Optional<PeisEncounterExtModel> findOptionalByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return java.util.Optional.empty();
        }
        return repository.findByEncounterIdAndIsDeletedFalse(encounterId).map(this::toModel);
    }

    /**
     * 按就诊主键批量加载扩展。
     *
     * @param encounterIds 就诊主键集合
     * @return encounterId → 读模型
     */
    public Map<Long, PeisEncounterExtModel> mapByEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PeisEncounterExt> rows = repository.findByEncounterIdInAndIsDeletedFalse(encounterIds);
        Map<Long, PeisEncounterExtModel> result = new HashMap<>();
        for (PeisEncounterExt row : rows) {
            if (row != null && row.getEncounterId() != null) {
                result.put(row.getEncounterId(), toModel(row));
            }
        }
        return result;
    }

    /**
     * 按团检标志过滤就诊主键（保持原顺序）。
     *
     * @param encounterIds 候选就诊
     * @param groupFlag    {@code true} 仅团检；{@code false} 仅个检（非团检）
     * @return 过滤后的主键列表
     */
    public List<Long> filterEncounterIdsByGroupFlag(Collection<Long> encounterIds, boolean groupFlag) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, PeisEncounterExtModel> extByEncounter = mapByEncounterIds(encounterIds);
        List<Long> filtered = new ArrayList<>();
        for (Long encounterId : encounterIds) {
            if (encounterId == null) {
                continue;
            }
            PeisEncounterExtModel ext = extByEncounter.get(encounterId);
            if (ext == null) {
                continue;
            }
            boolean isGroup = Boolean.TRUE.equals(ext.getGroupFlag());
            if (groupFlag == isGroup) {
                filtered.add(encounterId);
            }
        }
        return filtered;
    }

    /**
     * 按体检流水号查询扩展。
     *
     * @param busNo 流水号
     * @return 读模型；不存在返回 null
     */
    public PeisEncounterExtModel findByBusNo(String busNo) {
        if (StringUtils.isEmpty(busNo)) {
            return null;
        }
        return repository.findByBusNoAndIsDeletedFalse(busNo.trim())
            .map(this::toModel)
            .orElse(null);
    }

    /**
     * 按预约主键查询扩展。
     *
     * @param appointmentId 预约主键
     * @return 读模型；不存在返回 null
     */
    public PeisEncounterExtModel findByBookingAppointmentId(Long appointmentId) {
        if (appointmentId == null) {
            return null;
        }
        return repository.findByBookingAppointmentIdAndIsDeletedFalse(appointmentId)
            .map(this::toModel)
            .orElse(null);
    }

    private PeisEncounterExtModel toModel(PeisEncounterExt entity) {
        PeisEncounterExtModel model = new PeisEncounterExtModel();
        model.setEncounterId(entity.getEncounterId());
        model.setPackageActivityId(entity.getPackageActivityId());
        model.setPackageSnapshotJson(entity.getPackageSnapshotJson());
        model.setGroupFlag(entity.getGroupFlag());
        model.setCompanyId(entity.getCompanyId());
        model.setContractId(entity.getContractId());
        model.setGroupBatchId(entity.getGroupBatchId());
        model.setBookingAppointmentId(entity.getBookingAppointmentId());
        model.setBusNo(entity.getBusNo());
        model.setReferrerName(entity.getReferrerName());
        model.setBreakfastFlag(entity.getBreakfastFlag());
        return model;
    }
}
