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

package com.openhis.health.domain.emergency.emgtriage.queries;

import com.openhis.health.domain.emergency.emgtriage.entity.EmgTriage;
import com.openhis.health.domain.emergency.emgtriage.entity.EmgTriageAssessment;
import com.openhis.health.domain.emergency.emgtriage.queries.model.EmgTriageByEncounterQueryModel;
import com.openhis.health.domain.emergency.emgtriage.queries.model.EmgTriageListItemQueryModel;
import com.openhis.health.domain.emergency.emgtriage.repository.EmgTriageAssessmentRepository;
import com.openhis.health.domain.emergency.emgtriage.repository.EmgTriageRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 急诊分诊读侧（SDD §5.2）；患者/科室/地点名称由应用层或后续联接扩展装配。
 */
@Service
@RequiredArgsConstructor
public class EmgTriageQueryService {

    private final EmgTriageRepository emgTriageRepository;
    private final EmgTriageAssessmentRepository emgTriageAssessmentRepository;

    public Page<EmgTriageListItemQueryModel> queryEmgTriageRecordsPaged(EmgTriagePageQuery filter, Pageable pageable) {
        Specification<EmgTriage> spec = buildPageSpec(filter);
        if (pageable.isUnpaged()) {
            List<EmgTriage> all = emgTriageRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "triageTime"));
            List<EmgTriageListItemQueryModel> content =
                    all.stream().map(this::toListItem).collect(Collectors.toList());
            return new PageImpl<>(content, pageable, content.size());
        }
        Pageable sorted =
                pageable.getSort().isSorted()
                        ? pageable
                        : PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize(),
                                Sort.by(Sort.Direction.DESC, "triageTime"));
        return emgTriageRepository.findAll(spec, sorted).map(this::toListItem);
    }

    public Optional<EmgTriageByEncounterQueryModel> getEmgTriageByEncounterId(Long encounterId) {
        return emgTriageRepository
                .findByEncounterIdAndTriageStatusEnumAndIsDeletedFalse(encounterId, EmgTriage.STATUS_ACTIVE)
                .map(this::toByEncounter);
    }

    /**
     * 按分诊主键加载有效态分诊详情（含评估子行）。
     *
     * @param emgTriageId 分诊主键
     * @return 详情模型；无有效记录时为空
     */
    public Optional<EmgTriageByEncounterQueryModel> getEmgTriageByActiveId(Long emgTriageId) {
        if (emgTriageId == null) {
            return Optional.empty();
        }
        return emgTriageRepository
                .findByIdAndTriageStatusEnumAndIsDeletedFalse(emgTriageId, EmgTriage.STATUS_ACTIVE)
                .map(this::toByEncounter);
    }

    /**
     * 待二次分诊队列分页：当前实现将会话维度筛选折叠为机构下的有效分诊分页，
     * SLA 命中规则待机构参数与就诊接诊时刻联接补齐后再下推 SQL。
     *
     * @param filter   筛选条件
     * @param pageable 分页
     * @return 候选分页行
     */
    public Page<EmgTriageListItemQueryModel> queryPendingSecondaryTriagePage(
            EmgTriagePendingSecondaryQuery filter, Pageable pageable) {
        EmgTriagePageQuery mapped = new EmgTriagePageQuery();
        if (filter != null) {
            mapped.setOrganizationId(filter.getOrganizationId());
        }
        return queryEmgTriageRecordsPaged(mapped, pageable);
    }

    private Specification<EmgTriage> buildPageSpec(EmgTriagePageQuery q) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get("isDeleted")));
            if (!(Boolean.TRUE.equals(q.getIncludeAllTriageStatuses()) && q.getPatientId() != null)) {
                ps.add(cb.equal(root.get("triageStatusEnum"), EmgTriage.STATUS_ACTIVE));
            }
            if (q == null) {
                return cb.and(ps.toArray(new Predicate[0]));
            }
            if (q.getOrganizationId() != null) {
                ps.add(cb.equal(root.get("organizationId"), q.getOrganizationId()));
            }
            if (q.getPatientId() != null) {
                ps.add(cb.equal(root.get("patientId"), q.getPatientId()));
            }
            if (q.getEncounterId() != null) {
                ps.add(cb.equal(root.get("encounterId"), q.getEncounterId()));
            }
            if (q.getTriageLevelEnum() != null) {
                ps.add(cb.equal(root.get("triageLevelEnum"), q.getTriageLevelEnum()));
            }
            if (StringUtils.isNotBlank(q.getPatientNameHint())) {
                // DOC-CONFLICT：姓名检索依赖患者域联接或投影表，此处仅占位。
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private EmgTriageListItemQueryModel toListItem(EmgTriage e) {
        EmgTriageListItemQueryModel m = new EmgTriageListItemQueryModel();
        m.setEmgTriageId(e.getId());
        m.setEncounterId(e.getEncounterId());
        m.setPatientId(e.getPatientId());
        m.setTriageTime(e.getTriageTime());
        m.setTriageStatusEnum(e.getTriageStatusEnum());
        m.setTriageLevelEnum(e.getTriageLevelEnum());
        m.setSuggestedLevelEnum(e.getSuggestedLevelEnum());
        m.setOrganizationId(e.getOrganizationId());
        m.setLocationId(e.getLocationId());
        m.setSecondaryLocationId(e.getSecondaryLocationId());
        m.setDestinationModeEnum(e.getDestinationModeEnum());
        m.setChiefComplaintJson(e.getChiefComplaintJson());
        m.setSymptomJson(e.getSymptomJson());
        m.setDiseaseTypesJson(e.getDiseaseTypesJson());
        m.setPatientSource(e.getPatientSource());
        m.setQueuePriority(e.getQueuePriority());
        m.setIsGreenChannel(e.getIsGreenChannel());
        m.setIsStaged(e.getIsStaged());
        m.setTriageRegistrationModeEnum(e.getTriageRegistrationModeEnum());
        m.setMciEventId(e.getMciEventId());
        return m;
    }

    private EmgTriageByEncounterQueryModel toByEncounter(EmgTriage e) {
        EmgTriageByEncounterQueryModel m = new EmgTriageByEncounterQueryModel();
        m.setId(e.getId());
        m.setEncounterId(e.getEncounterId());
        m.setPatientId(e.getPatientId());
        m.setTriageTime(e.getTriageTime());
        m.setTriageStatusEnum(e.getTriageStatusEnum());
        m.setChiefComplaintJson(e.getChiefComplaintJson());
        m.setSymptomJson(e.getSymptomJson());
        m.setTriageLevelEnum(e.getTriageLevelEnum());
        m.setSuggestedLevelEnum(e.getSuggestedLevelEnum());
        m.setLevelAdjustReason(e.getLevelAdjustReason());
        m.setOrganizationId(e.getOrganizationId());
        m.setLocationId(e.getLocationId());
        m.setAssignedDoctorSnapshot(e.getAssignedDoctorSnapshot());
        m.setDiseaseTypesJson(e.getDiseaseTypesJson());
        m.setPatientSource(e.getPatientSource());
        m.setIsGreenChannel(e.getIsGreenChannel());
        m.setIsFreeRegister(e.getIsFreeRegister());
        m.setQueuePriority(e.getQueuePriority());
        m.setNote(e.getNote());
        m.setTriageRegistrationModeEnum(e.getTriageRegistrationModeEnum());
        m.setIsStaged(e.getIsStaged());
        m.setStageQueueSeq(e.getStageQueueSeq());
        m.setProvisionalTriageNo(e.getProvisionalTriageNo());
        m.setSpecialPopulationTagsJson(e.getSpecialPopulationTagsJson());
        m.setMciEventId(e.getMciEventId());
        m.setBlacklistAlertAck(e.getBlacklistAlertAck());
        m.setArrivalModeEnum(e.getArrivalModeEnum());
        m.setTransferInOrgId(e.getTransferInOrgId());
        m.setDestinationModeEnum(e.getDestinationModeEnum());
        m.setSecondaryLocationId(e.getSecondaryLocationId());
        m.setGreenChannelOnsetTime(e.getGreenChannelOnsetTime());
        m.setEpidemicSurveyJson(e.getEpidemicSurveyJson());
        m.setSpecialtyGreenChannelCode(e.getSpecialtyGreenChannelCode());
        m.setAmbulance120Json(e.getAmbulance120Json());
        m.setPolice110Json(e.getPolice110Json());
        m.setMonitorDeviceBindingJson(e.getMonitorDeviceBindingJson());
        m.setWristbandPrintedAt(e.getWristbandPrintedAt());
        m.setVitalsAutoTierPolicyId(e.getVitalsAutoTierPolicyId());
        m.setChangeReasonCode(e.getChangeReasonCode());
        m.setChangeRemark(e.getChangeRemark());
        m.setLastAmendedBy(e.getLastAmendedBy());
        m.setLastAmendedAt(e.getLastAmendedAt());
        m.setPriorEmgTriageId(e.getPriorEmgTriageId());
        m.setSupersededById(e.getSupersededById());

        List<EmgTriageAssessment> assessments = emgTriageAssessmentRepository.findByEmgTriageIdAndIsDeletedFalse(e.getId());
        m.getAssessments().addAll(assessments);
        return m;
    }
}
