package com.openhis.whale.booking.adapter.port;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.repository.EncounterRepository;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.booking.contracts.dto.EncounterSearchResultDto;
import com.openhis.whale.booking.contracts.dto.EncounterSummaryDto;
import com.openhis.whale.booking.contracts.port.BookingEncounterPort;
import com.openhis.whale.booking.contracts.query.SearchEncounterQuery;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link BookingEncounterPort} 实现：读 health Encounter。
 */
@Component
@RequiredArgsConstructor
public class BookingEncounterPortAdapter implements BookingEncounterPort {

    private final EncounterQueryService encounterQueryService;
    private final EncounterRepository encounterRepository;
    private final PatientQueryService patientQueryService;
    private final OrganizationQueryService organizationQueryService;

    @Override
    public Optional<EncounterSummaryDto> loadEncounterSummary(Long encounterId) {
        if (encounterId == null) {
            return Optional.empty();
        }
        Optional<Encounter> encounterOptional = encounterQueryService.findOptionalEncounterEntityById(encounterId);
        if (encounterOptional.isEmpty()) {
            return Optional.empty();
        }
        Encounter encounter = encounterOptional.get();
        EncounterSummaryDto summary = new EncounterSummaryDto();
        summary.setEncounterId(encounter.getId());
        summary.setPatientId(encounter.getPatientId());
        summary.setOrganizationId(encounter.getOrganizationId());
        summary.setBusNo(encounter.getBusNo());
        return Optional.of(summary);
    }

    @Override
    public Optional<EncounterSearchResultDto> searchEncounter(SearchEncounterQuery query) {
        if (query == null || query.getOrganizationId() == null) {
            return Optional.empty();
        }
        Encounter encounter = this.resolveEncounter(query);
        if (encounter == null || encounter.getId() == null) {
            return Optional.empty();
        }
        if (!query.getOrganizationId().equals(encounter.getOrganizationId())) {
            return Optional.empty();
        }
        EncounterSearchResultDto result = new EncounterSearchResultDto();
        result.setEncounterId(encounter.getId());
        result.setEncounterBusNo(encounter.getBusNo());
        result.setPatientId(encounter.getPatientId());
        result.setOrganizationId(encounter.getOrganizationId());
        if (encounter.getPatientId() != null) {
            Map<Long, String> patientNames = patientQueryService.findPatientNamesByIds(List.of(encounter.getPatientId()));
            result.setPatientName(patientNames.get(encounter.getPatientId()));
        }
        if (encounter.getOrganizationId() != null) {
            Map<Long, String> orgNames = organizationQueryService.findOrganizationNamesByIds(List.of(encounter.getOrganizationId()));
            result.setOrganizationName(orgNames.get(encounter.getOrganizationId()));
        }
        return Optional.of(result);
    }

    private Encounter resolveEncounter(SearchEncounterQuery query) {
        if (StringUtils.isNotBlank(query.getEncounterBusNo())) {
            return encounterRepository.findByBusNo(query.getEncounterBusNo().trim()).orElse(null);
        }
        if (query.getPatientId() != null) {
            return this.findLatestEncounterByPatientAndOrganization(query.getPatientId(), query.getOrganizationId());
        }
        if (StringUtils.isNotBlank(query.getKeyword())) {
            return this.findByKeyword(query.getKeyword().trim(), query.getOrganizationId());
        }
        return null;
    }

    private Encounter findLatestEncounterByPatientAndOrganization(Long patientId, Long organizationId) {
        Specification<Encounter> specification = (root, cq, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.PATIENT_ID), patientId),
            cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
        List<Encounter> rows = encounterRepository.findAll(specification,
            Sort.by(Sort.Direction.DESC, FieldNameConstants.ID));
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    private Encounter findByKeyword(String keyword, Long organizationId) {
        String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(keyword);
        Specification<Encounter> busNoSpec = (root, cq, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId),
            cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc));
        List<Encounter> byBusNo = encounterRepository.findAll(busNoSpec, Sort.by(Sort.Direction.DESC, FieldNameConstants.ID));
        if (!byBusNo.isEmpty()) {
            return byBusNo.get(0);
        }
        List<Long> patientIds = patientQueryService.listPatientIdsByEncounterListKeyword(keyword);
        if (patientIds.isEmpty()) {
            return null;
        }
        Specification<Encounter> patientSpec = (root, cq, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId),
            root.get(FieldNameConstants.PATIENT_ID).in(patientIds));
        List<Encounter> byPatient = encounterRepository.findAll(patientSpec, Sort.by(Sort.Direction.DESC, FieldNameConstants.ID));
        if (byPatient.isEmpty()) {
            return null;
        }
        return byPatient.get(0);
    }
}
