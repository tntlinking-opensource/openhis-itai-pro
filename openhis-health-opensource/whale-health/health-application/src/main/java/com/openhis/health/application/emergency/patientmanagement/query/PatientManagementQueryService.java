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

package com.openhis.health.application.emergency.patientmanagement.query;

import com.openhis.health.application.emergency.patientmanagement.query.model.PatientManagementListItemQueryModel;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.entity.EncounterLocation;
import com.openhis.health.domain.administration.encounter.repository.EncounterLocationRepository;
import com.openhis.health.domain.administration.encounter.repository.EncounterRepository;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.repository.LocationRepository;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.repository.PatientRepository;
import com.openhis.health.domain.emergency.emgtriage.entity.EmgTriage;
import com.openhis.health.domain.emergency.emgtriage.repository.EmgTriageRepository;
import com.openhis.health.domain.emergency.patientalert.entity.PatientAlert;
import com.openhis.health.domain.emergency.patientalert.repository.PatientAlertRepository;
import com.openhis.health.domain.emergency.patientfocus.entity.EmgPatientFocus;
import com.openhis.health.domain.emergency.patientfocus.repository.EmgPatientFocusRepository;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.EncounterActivityStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.utils.AgeCalculatorUtil;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 患者管理工作站列表读侧（应用层编排查询，无 patientmanagement 领域聚合根）。
 * <p>以有效分诊为锚联接就诊、患者、床位与重点关注。</p>
 */
@Service
@RequiredArgsConstructor
public class PatientManagementQueryService {

    private static final List<String> ENCOUNTER_STATUS_IN = List.of(
        EncounterStatus.REGISTERED.getCode(),
        EncounterStatus.TRIAGED.getCode(),
        EncounterStatus.IN_PROGRESS.getCode(),
        EncounterStatus.ON_HOLD.getCode()
    );

    private final EmgTriageRepository emgTriageRepository;
    private final EncounterRepository encounterRepository;
    private final PatientRepository patientRepository;
    private final LocationRepository locationRepository;
    private final EncounterLocationRepository encounterLocationRepository;
    private final EmgPatientFocusRepository emgPatientFocusRepository;
    private final PatientAlertRepository patientAlertRepository;

    public Page<PatientManagementListItemQueryModel> pagePatients(PatientManagementPageQuery filter) {
        int pageNo = 1;
        if (filter != null && filter.getPageNo() != null && filter.getPageNo() > 0) {
            pageNo = filter.getPageNo();
        }
        int pageSize = 50;
        if (filter != null && filter.getPageSize() != null && filter.getPageSize() > 0) {
            pageSize = Math.min(filter.getPageSize(), 500);
        }
        Pageable pageable = PageRequest.of(
            pageNo - 1,
            pageSize,
            Sort.by(Sort.Order.desc("triageTime"), Sort.Order.desc("id"))
        );
        Specification<EmgTriage> spec = buildSpec(filter);
        Page<EmgTriage> triagePage = emgTriageRepository.findAll(spec, pageable);
        List<PatientManagementListItemQueryModel> rows = assembleRows(triagePage.getContent());
        return new PageImpl<>(rows, pageable, triagePage.getTotalElements());
    }

    /**
     * 有效分诊已使用的去向区域（去重）；不按机构过滤。
     */
    /**
     * 列出区域下可用床位（形态为床且启用）。
     */
    public List<Location> listBedsByAreaLocation(Long areaLocationId) {
        if (areaLocationId == null) {
            return List.of();
        }
        return locationRepository.findAllByParentId(areaLocationId).stream()
            .filter(loc -> LocationForm.BED.getCode().equals(loc.getFormEnum()))
            .filter(loc -> LocationStatus.ACTIVE.getCode().equals(loc.getStatusEnum()))
            .collect(Collectors.toList());
    }

    public List<Location> listDestinationAreas() {
        Specification<EmgTriage> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get("isDeleted")));
            ps.add(cb.equal(root.get("triageStatusEnum"), EmgTriage.STATUS_ACTIVE));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Set<Long> locationIds = emgTriageRepository.findAll(spec).stream()
            .map(EmgTriage::getLocationId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (locationIds.isEmpty()) {
            return List.of();
        }
        return locationRepository.findAllById(locationIds).stream()
            .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
            .sorted((a, b) -> StringUtils.compareIgnoreCase(
                StringUtils.defaultString(a.getName()),
                StringUtils.defaultString(b.getName())))
            .toList();
    }

    private Specification<EmgTriage> buildSpec(PatientManagementPageQuery q) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get("isDeleted")));
            ps.add(cb.equal(root.get("triageStatusEnum"), EmgTriage.STATUS_ACTIVE));
            if (q == null) {
                return cb.and(ps.toArray(new Predicate[0]));
            }
            if (q.getLocationId() != null) {
                ps.add(cb.equal(root.get("locationId"), q.getLocationId()));
            }
            applyEncounterSubqueryPredicates(q, root, query, cb, ps);
            applySearchPredicates(q, root, query, cb, ps);
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private void applyEncounterSubqueryPredicates(
            PatientManagementPageQuery q,
            Root<EmgTriage> triageRoot,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            List<Predicate> ps) {
        Subquery<Long> encSub = query.subquery(Long.class);
        Root<Encounter> enc = encSub.from(Encounter.class);
        encSub.select(enc.get(FieldNameConstants.ID));
        List<Predicate> ep = new ArrayList<>();
        ep.add(cb.equal(enc.get(FieldNameConstants.ID), triageRoot.get("encounterId")));
        ep.add(cb.isFalse(enc.get(FieldNameConstants.IS_DELETED)));
        ep.add(cb.equal(enc.get(FieldNameConstants.CLASS_ENUM), EncounterClass.EMER.getCode()));
        ep.add(enc.get(FieldNameConstants.STATUS_ENUM).in(ENCOUNTER_STATUS_IN));
        LocalDateTime now = LocalDateTime.now();
        String regFilter = StringUtils.trimToEmpty(q.getRegistrationTimeFilter());
        if (StringUtils.isNotBlank(regFilter) && !"ALL".equalsIgnoreCase(regFilter)) {
            switch (regFilter.toUpperCase()) {
                case "WITHIN_60" -> ep.add(cb.greaterThanOrEqualTo(enc.get(FieldNameConstants.START_TIME), now.minusMinutes(60)));
                case "WITHIN_120" -> ep.add(cb.greaterThanOrEqualTo(enc.get(FieldNameConstants.START_TIME), now.minusMinutes(120)));
                case "OVER_30" -> ep.add(cb.lessThanOrEqualTo(enc.get(FieldNameConstants.START_TIME), now.minusMinutes(30)));
                case "OVER_60" -> ep.add(cb.lessThanOrEqualTo(enc.get(FieldNameConstants.START_TIME), now.minusMinutes(60)));
                default -> { /* ignore unknown */ }
            }
        }
        encSub.where(cb.and(ep.toArray(new Predicate[0])));
        ps.add(triageRoot.get("encounterId").in(encSub));
    }

    private void applySearchPredicates(
            PatientManagementPageQuery q,
            Root<EmgTriage> triageRoot,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            List<Predicate> ps) {
        if (q.isExactSearch()) {
            if (q.getPatientId() != null) {
                ps.add(cb.equal(triageRoot.get("patientId"), q.getPatientId()));
            }
            if (StringUtils.isNotBlank(q.getPatientName())) {
                Subquery<Long> patientSub = query.subquery(Long.class);
                Root<Patient> pr = patientSub.from(Patient.class);
                patientSub.select(pr.get(FieldNameConstants.ID));
                patientSub.where(cb.and(
                    cb.isFalse(pr.get(FieldNameConstants.IS_DELETED)),
                    cb.equal(cb.lower(pr.get(FieldNameConstants.NAME)), q.getPatientName().trim().toLowerCase())
                ));
                ps.add(triageRoot.get("patientId").in(patientSub));
            }
            if (StringUtils.isNotBlank(q.getBedLabel())) {
                String bed = q.getBedLabel().trim().toLowerCase();
                Subquery<Long> bedExists = query.subquery(Long.class);
                Root<EncounterLocation> el = bedExists.from(EncounterLocation.class);
                Root<Location> loc = bedExists.from(Location.class);
                bedExists.select(cb.literal(1L));
                bedExists.where(cb.and(
                    cb.equal(el.get("encounterId"), triageRoot.get("encounterId")),
                    cb.equal(el.get("statusEnum"), EncounterActivityStatus.ACTIVE.getCode()),
                    cb.equal(el.get("locationId"), loc.get(FieldNameConstants.ID)),
                    cb.isFalse(loc.get(FieldNameConstants.IS_DELETED)),
                    cb.or(
                        cb.equal(cb.lower(loc.get("name")), bed),
                        cb.equal(cb.lower(loc.get("busNo")), bed)
                    )
                ));
                ps.add(cb.exists(bedExists));
            }
            return;
        }
        if (StringUtils.isBlank(q.getKeyword())) {
            return;
        }
        String trimmed = q.getKeyword().trim();
        String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
        Subquery<Long> patientSub = query.subquery(Long.class);
        Root<Patient> pr = patientSub.from(Patient.class);
        patientSub.select(pr.get(FieldNameConstants.ID));
        patientSub.where(cb.and(
            cb.isFalse(pr.get(FieldNameConstants.IS_DELETED)),
            cb.or(
                cb.like(cb.lower(pr.get(FieldNameConstants.NAME)), likeIc),
                cb.like(cb.lower(pr.get(FieldNameConstants.BUS_NO)), likeIc),
                cb.like(cb.lower(pr.get(FieldNameConstants.ID_CARD)), likeIc),
                cb.like(cb.lower(pr.get(FieldNameConstants.PY_STR)), likeIc),
                cb.like(cb.lower(pr.get(FieldNameConstants.WB_STR)), likeIc)
            )
        ));
        Subquery<Long> encSub = query.subquery(Long.class);
        Root<Encounter> enc = encSub.from(Encounter.class);
        encSub.select(enc.get(FieldNameConstants.ID));
        encSub.where(cb.and(
            cb.equal(enc.get(FieldNameConstants.ID), triageRoot.get("encounterId")),
            cb.like(cb.lower(enc.get(FieldNameConstants.BUS_NO)), likeIc)
        ));
        if (StringUtils.isNumeric(trimmed)) {
            try {
                long pid = Long.parseLong(trimmed);
                ps.add(cb.or(
                    cb.equal(triageRoot.get("patientId"), pid),
                    triageRoot.get("patientId").in(patientSub),
                    cb.exists(encSub)
                ));
                return;
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        ps.add(cb.or(triageRoot.get("patientId").in(patientSub), cb.exists(encSub)));
    }

    private List<PatientManagementListItemQueryModel> assembleRows(List<EmgTriage> triages) {
        if (triages.isEmpty()) {
            return List.of();
        }
        Set<Long> encounterIds = triages.stream().map(EmgTriage::getEncounterId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> patientIds = triages.stream().map(EmgTriage::getPatientId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> locationIds = triages.stream().map(EmgTriage::getLocationId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, Encounter> encounterById = new HashMap<>();
        for (Encounter e : encounterRepository.findAllById(encounterIds)) {
            if (e.getId() != null) {
                encounterById.put(e.getId(), e);
            }
        }
        Map<Long, Patient> patientById = new HashMap<>();
        for (Patient p : patientRepository.findAllById(patientIds)) {
            if (p.getId() != null) {
                patientById.put(p.getId(), p);
            }
        }
        Map<Long, String> locationNameById = new HashMap<>();
        for (Location loc : locationRepository.findAllById(locationIds)) {
            if (loc.getId() != null) {
                locationNameById.put(loc.getId(), loc.getName());
            }
        }
        Map<Long, String> bedByEncounterId = resolveActiveBedLabels(encounterIds);
        Map<Long, EmgPatientFocus> focusByEncounterId = new HashMap<>();
        for (EmgPatientFocus f : emgPatientFocusRepository.findAllByEncounterIdInAndStatusEnumAndIsDeletedFalse(
            encounterIds, EmgPatientFocus.STATUS_ACTIVE)) {
            if (f.getEncounterId() != null) {
                focusByEncounterId.put(f.getEncounterId(), f);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        Set<Long> blacklistedPatientIds = new LinkedHashSet<>();
        if (!patientIds.isEmpty()) {
            for (PatientAlert alert : patientAlertRepository.findAllByPatientIdInAndStatusEnumAndAlertTypeEnumAndIsDeletedFalse(
                patientIds, PatientAlert.STATUS_ACTIVE, PatientAlert.TYPE_BLACKLIST)) {
                if (alert.getPatientId() != null && alert.isEffectiveAt(now)) {
                    blacklistedPatientIds.add(alert.getPatientId());
                }
            }
        }
        List<PatientManagementListItemQueryModel> rows = new ArrayList<>();
        for (EmgTriage t : triages) {
            PatientManagementListItemQueryModel m = new PatientManagementListItemQueryModel();
            m.setEmgTriageId(t.getId());
            m.setEncounterId(t.getEncounterId());
            m.setPatientId(t.getPatientId());
            m.setTriageLevelEnum(t.getTriageLevelEnum());
            m.setLocationId(t.getLocationId());
            m.setAreaName(locationNameById.getOrDefault(t.getLocationId(), ""));
            m.setGreenChannel(Boolean.TRUE.equals(t.getIsGreenChannel()));
            m.setSpecialPopulationTagsJson(t.getSpecialPopulationTagsJson());
            m.setBlacklisted(t.getPatientId() != null && blacklistedPatientIds.contains(t.getPatientId()));
            m.setBedLabel(bedByEncounterId.getOrDefault(t.getEncounterId(), "—"));

            Encounter enc = encounterById.get(t.getEncounterId());
            if (enc != null) {
                m.setRegistrationTime(enc.getStartTime());
                m.setEncounterBusNo(enc.getBusNo());
                if (enc.getStartTime() != null) {
                    m.setWaitMinutes(Math.max(0, ChronoUnit.MINUTES.between(enc.getStartTime(), now)));
                }
            }
            Patient patient = patientById.get(t.getPatientId());
            if (patient != null) {
                m.setPatientName(patient.getName());
                m.setGenderEnum(patient.getGenderEnum());
                AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, patient.getGenderEnum());
                if (gender != null) {
                    m.setGenderDisplay(gender.getDisplay());
                }
                int years = AgeCalculatorUtil.calculateAge(patient.getBirthDate());
                if (years > 0) {
                    m.setAgeYears(years);
                }
            }
            EmgPatientFocus focus = focusByEncounterId.get(t.getEncounterId());
            if (focus != null) {
                m.setFocused(true);
                m.setFocusNote(focus.getFocusNote());
            }
            rows.add(m);
        }
        return rows;
    }

    private Map<Long, String> resolveActiveBedLabels(Set<Long> encounterIds) {
        Map<Long, String> result = new HashMap<>();
        if (encounterIds.isEmpty()) {
            return result;
        }
        List<EncounterLocation> activeLocs = encounterLocationRepository.findAllByEncounterIdInAndStatusEnum(
            encounterIds, EncounterActivityStatus.ACTIVE.getCode());
        Set<Long> bedIds = activeLocs.stream()
            .map(EncounterLocation::getLocationId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, Location> locById = new HashMap<>();
        for (Location loc : locationRepository.findAllById(bedIds)) {
            if (loc.getId() != null) {
                locById.put(loc.getId(), loc);
            }
        }
        for (EncounterLocation el : activeLocs) {
            if (el.getEncounterId() == null || el.getLocationId() == null) {
                continue;
            }
            Location loc = locById.get(el.getLocationId());
            if (loc == null) {
                continue;
            }
            String label;
            if (StringUtils.isNotBlank(loc.getName())) {
                label = loc.getName();
            } else {
                label = loc.getBusNo();
            }
            result.putIfAbsent(el.getEncounterId(), label);
        }
        return result;
    }
}
