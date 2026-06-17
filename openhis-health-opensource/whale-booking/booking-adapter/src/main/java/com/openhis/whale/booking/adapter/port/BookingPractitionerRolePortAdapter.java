package com.openhis.whale.booking.adapter.port;

import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRoleScheduleItem;
import com.openhis.health.domain.administration.practitionerrole.queries.model.PractitionerHealthcareServiceQueryModel;
import com.openhis.health.domain.administration.practitionerrole.queries.model.PractitionerRoleAssignmentQueryModel;
import com.openhis.health.domain.administration.practitionerrole.repository.PractitionerRoleScheduleItemRepository;
import com.openhis.health.domain.administration.practitionerrole.service.PractitionerRoleQueryService;
import com.openhis.whale.booking.adapter.support.PractitionerRoleScheduleJsonSupport;
import com.openhis.whale.booking.contracts.dto.PractitionerRoleScheduleItemDto;
import com.openhis.whale.booking.contracts.port.BookingPractitionerRolePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link BookingPractitionerRolePort} 实现：读 health 岗位排班子表。
 */
@Component
@RequiredArgsConstructor
public class BookingPractitionerRolePortAdapter implements BookingPractitionerRolePort {

    private final PractitionerRoleQueryService practitionerRoleQueryService;
    private final PractitionerRoleScheduleItemRepository scheduleItemRepository;

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
            scheduleItemRepository.findAllByAssignmentIdIn(assignmentIds);
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
                PractitionerRoleScheduleJsonSupport.parseAvailabilityJson(scheduleItem.getAvailabilityJson());
            List<Long> serviceIds = serviceIdsByAssignmentId.get(scheduleItem.getAssignmentId());
            if (serviceIds == null || serviceIds.isEmpty()) {
                result.add(this.mergeAssignment(parsed, assignment, null));
                continue;
            }
            for (Long serviceId : serviceIds) {
                result.add(this.mergeAssignment(parsed, assignment, serviceId));
            }
        }
        return result;
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
