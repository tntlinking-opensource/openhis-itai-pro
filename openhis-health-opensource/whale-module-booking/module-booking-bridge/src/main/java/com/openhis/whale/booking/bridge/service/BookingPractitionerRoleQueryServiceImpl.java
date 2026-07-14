/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.bridge.service;

import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRoleScheduleItem;
import com.openhis.health.domain.administration.practitionerrole.queries.model.PractitionerHealthcareServiceQueryModel;
import com.openhis.health.domain.administration.practitionerrole.queries.model.PractitionerRoleAssignmentQueryModel;
import com.openhis.health.domain.administration.practitionerrole.service.PractitionerRoleQueryService;
import com.openhis.whale.booking.bridge.masterdata.context.PractitionerRoleScheduleJsonMapping;
import com.openhis.whale.booking.domain.masterdata.result.PractitionerRoleScheduleItemDto;
import com.openhis.whale.booking.domain.masterdata.service.BookingPractitionerRoleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BookingPractitionerRoleQueryServiceImpl implements BookingPractitionerRoleQueryService {

    private final PractitionerRoleQueryService practitionerRoleQueryService;

    @Override
    public List<PractitionerRoleScheduleItemDto> listScheduleItemsByPractitioner(
        Long practitionerId, Long organizationId) {
        if (practitionerId == null || organizationId == null) {
            return new ArrayList<>();
        }
        List<PractitionerRoleAssignmentQueryModel> assignments =
            practitionerRoleQueryService.listAssignmentsByPractitioner(practitionerId);
        Map<Long, PractitionerRoleAssignmentQueryModel> assignmentById = new LinkedHashMap<>();
        List<Long> assignmentIds = new ArrayList<>();
        for (PractitionerRoleAssignmentQueryModel assignment : assignments) {
            if (assignment == null || assignment.getAssignmentId() == null) {
                continue;
            }
            if (!Objects.equals(organizationId, assignment.getOrgId())) {
                continue;
            }
            assignmentById.put(assignment.getAssignmentId(), assignment);
            assignmentIds.add(assignment.getAssignmentId());
        }
        if (assignmentIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<PractitionerHealthcareServiceQueryModel> serviceRows =
            practitionerRoleQueryService.listHealthcareServiceIdsByPractitionerAndOrg(practitionerId, organizationId);
        Map<Long, List<Long>> serviceIdsByAssignmentId = new LinkedHashMap<>();
        for (PractitionerHealthcareServiceQueryModel row : serviceRows) {
            if (row == null || row.getAssignmentId() == null || row.getHealthcareServiceId() == null) {
                continue;
            }
            serviceIdsByAssignmentId
                .computeIfAbsent(row.getAssignmentId(), key -> new ArrayList<>())
                .add(row.getHealthcareServiceId());
        }

        List<PractitionerRoleScheduleItem> scheduleItems =
            practitionerRoleQueryService.listScheduleItemsByAssignmentIds(assignmentIds);
        List<PractitionerRoleScheduleItemDto> result = new ArrayList<>();
        for (PractitionerRoleScheduleItem scheduleItem : scheduleItems) {
            if (scheduleItem == null || scheduleItem.getAssignmentId() == null) {
                continue;
            }
            PractitionerRoleAssignmentQueryModel assignment =
                assignmentById.get(scheduleItem.getAssignmentId());
            if (assignment == null) {
                continue;
            }
            PractitionerRoleScheduleItemDto parsed =
                PractitionerRoleScheduleJsonMapping.parseAvailabilityJson(scheduleItem.getAvailabilityJson());
            List<Long> serviceIds = serviceIdsByAssignmentId.get(scheduleItem.getAssignmentId());
            if (serviceIds == null || serviceIds.isEmpty()) {
                result.add(this.mergeAssignment(parsed, assignment, null));
                continue;
            }
            for (Long serviceId : serviceIds) {
                result.add(this.mergeAssignment(parsed, assignment, serviceId));
            }
        }
        if (result.isEmpty()) {
            result.addAll(this.buildFallbackFromAssignments(assignmentById, serviceIdsByAssignmentId));
        }
        return result;
    }

    private List<PractitionerRoleScheduleItemDto> buildFallbackFromAssignments(
        Map<Long, PractitionerRoleAssignmentQueryModel> assignmentById,
        Map<Long, List<Long>> serviceIdsByAssignmentId) {
        List<PractitionerRoleScheduleItemDto> fallback = new ArrayList<>();
        for (Map.Entry<Long, PractitionerRoleAssignmentQueryModel> entry : assignmentById.entrySet()) {
            Long assignmentId = entry.getKey();
            PractitionerRoleAssignmentQueryModel assignment = entry.getValue();
            if (assignment == null) {
                continue;
            }
            List<Long> serviceIds = serviceIdsByAssignmentId.get(assignmentId);
            if (serviceIds == null || serviceIds.isEmpty()) {
                continue;
            }
            for (Long serviceId : serviceIds) {
                PractitionerRoleScheduleItemDto dto = new PractitionerRoleScheduleItemDto();
                dto.setAssignmentId(assignmentId);
                dto.setLocationId(assignment.getLocationId());
                dto.setHealthcareServiceId(serviceId);
                fallback.add(dto);
            }
        }
        return fallback;
    }

    private PractitionerRoleScheduleItemDto mergeAssignment(
        PractitionerRoleScheduleItemDto parsed,
        PractitionerRoleAssignmentQueryModel assignment,
        Long healthcareServiceId) {
        PractitionerRoleScheduleItemDto dto = new PractitionerRoleScheduleItemDto();
        dto.setAssignmentId(assignment.getAssignmentId());
        dto.setDayOfWeek(parsed.getDayOfWeek());
        dto.setTimePeriodsJson(parsed.getTimePeriodsJson());
        dto.setLocationId(assignment.getLocationId());
        dto.setHealthcareServiceId(healthcareServiceId);
        return dto;
    }
}
