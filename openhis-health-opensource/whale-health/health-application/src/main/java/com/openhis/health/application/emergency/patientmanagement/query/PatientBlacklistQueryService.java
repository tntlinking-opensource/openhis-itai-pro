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

import com.openhis.health.application.emergency.patientmanagement.dto.PatientBlacklistRowDto;
import com.openhis.health.application.emergency.patientmanagement.service.PatientDisplayFlagAssembler;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.repository.PatientRepository;
import com.openhis.health.domain.emergency.patientalert.entity.PatientAlert;
import com.openhis.health.domain.emergency.patientalert.repository.PatientAlertRepository;
import com.openhis.health.domain.share.constants.FieldNameConstants;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatientBlacklistQueryService {

    private final PatientAlertRepository patientAlertRepository;
    private final PatientRepository patientRepository;

    public Page<PatientBlacklistRowDto> pageBlacklist(PatientBlacklistPageQuery filter) {
        int pageNo = 1;
        if (filter != null && filter.getPageNo() != null && filter.getPageNo() > 0) {
            pageNo = filter.getPageNo();
        }
        int pageSize = 20;
        if (filter != null && filter.getPageSize() != null && filter.getPageSize() > 0) {
            pageSize = Math.min(filter.getPageSize(), 200);
        }
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Order.desc("createdAt")));
        LocalDateTime now = LocalDateTime.now();
        Specification<PatientAlert> spec = buildSpec(filter, now);
        Page<PatientAlert> page = patientAlertRepository.findAll(spec, pageable);
        Set<Long> patientIds = page.getContent().stream()
            .map(PatientAlert::getPatientId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, Patient> patientById = new HashMap<>();
        for (Patient p : patientRepository.findAllById(patientIds)) {
            if (p.getId() != null) {
                patientById.put(p.getId(), p);
            }
        }
        List<PatientBlacklistRowDto> rows = new ArrayList<>();
        for (PatientAlert alert : page.getContent()) {
            PatientBlacklistRowDto dto = new PatientBlacklistRowDto();
            dto.setAlertId(alert.getId());
            dto.setPatientId(alert.getPatientId());
            dto.setMarkTypeCode(alert.getMarkTypeCode());
            dto.setMarkTypeDisplay(PatientDisplayFlagAssembler.markTypeDisplay(alert.getMarkTypeCode()));
            dto.setReasonText(alert.getReasonText());
            dto.setCreatedAt(alert.getCreatedAt());
            dto.setCreatedBy(alert.getCreatedBy());
            Patient patient = patientById.get(alert.getPatientId());
            if (patient != null) {
                dto.setPatientName(patient.getName());
            }
            rows.add(dto);
        }
        return new PageImpl<>(rows, pageable, page.getTotalElements());
    }

    private static Specification<PatientAlert> buildSpec(PatientBlacklistPageQuery filter, LocalDateTime asOf) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            ps.add(cb.equal(root.get("statusEnum"), PatientAlert.STATUS_ACTIVE));
            ps.add(cb.equal(root.get("alertTypeEnum"), PatientAlert.TYPE_BLACKLIST));
            ps.add(cb.or(
                cb.isNull(root.get("effectiveFrom")),
                cb.lessThanOrEqualTo(root.get("effectiveFrom"), asOf)
            ));
            ps.add(cb.or(
                cb.isNull(root.get("effectiveTo")),
                cb.greaterThanOrEqualTo(root.get("effectiveTo"), asOf)
            ));
            if (filter != null && StringUtils.isNotBlank(filter.getMarkTypeCode())) {
                ps.add(cb.equal(root.get("markTypeCode"), filter.getMarkTypeCode().trim()));
            }
            if (filter != null && StringUtils.isNotBlank(filter.getPatientName())) {
                String pattern = SearchCodeUtil.toCaseInsensitiveLikePattern(filter.getPatientName().trim());
                Subquery<Long> patientSub = query.subquery(Long.class);
                Root<Patient> pr = patientSub.from(Patient.class);
                patientSub.select(pr.get(FieldNameConstants.ID));
                patientSub.where(cb.and(
                    cb.isFalse(pr.get(FieldNameConstants.IS_DELETED)),
                    cb.like(cb.lower(pr.get(FieldNameConstants.NAME)), pattern)
                ));
                ps.add(root.get("patientId").in(patientSub));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }
}
