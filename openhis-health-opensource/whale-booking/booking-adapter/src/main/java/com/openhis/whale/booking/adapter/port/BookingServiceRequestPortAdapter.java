package com.openhis.whale.booking.adapter.port;

import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.health.domain.workflow.servicerequest.repository.ServiceRequestRepository;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.booking.contracts.dto.BookableServiceRequestDto;
import com.openhis.whale.booking.contracts.port.BookingServiceRequestPort;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@link BookingServiceRequestPort} 实现：读 health ServiceRequest。
 */
@Component
@RequiredArgsConstructor
public class BookingServiceRequestPortAdapter implements BookingServiceRequestPort {

    private static final Set<String> BOOKABLE_CATEGORIES = Set.of(
        "PROOF", "TEST", "ENDOSCOPY", "ORAL_EXAMINATION");

    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ServiceRequestRepository serviceRequestRepository;

    @Override
    public List<BookableServiceRequestDto> listBookableServiceRequestsByEncounter(Long encounterId) {
        if (encounterId == null) {
            return new ArrayList<>();
        }
        List<ServiceRequestListModel> rows =
            serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        List<BookableServiceRequestDto> result = new ArrayList<>();
        for (ServiceRequestListModel row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!this.isBookableCategory(row.getCategoryEnum())) {
                continue;
            }
            if (!RequestStatus.ACTIVE.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            BookableServiceRequestDto dto = new BookableServiceRequestDto();
            dto.setServiceRequestId(row.getId());
            dto.setServiceRequestBusNo(this.resolveServiceRequestBusNo(row.getId()));
            dto.setActivityName(row.getReasonText());
            dto.setEncounterId(row.getEncounterId());
            dto.setPatientId(row.getPatientId());
            dto.setActivityId(row.getActivityId());
            dto.setCategoryEnum(row.getCategoryEnum());
            dto.setStatusEnum(row.getStatusEnum());
            dto.setReasonText(row.getReasonText());
            result.add(dto);
        }
        return result;
    }

    @Override
    public void markServiceRequestBooked(Long serviceRequestId, Long appointmentId) {
        // health-domain 暂无医嘱预约回写命令；关联由 booking-domain Appointment 承载。
    }

    @Override
    public Long resolveActivityIdByServiceRequestId(Long serviceRequestId) {
        return serviceRequestQueryService.resolveActivityIdByServiceRequestId(serviceRequestId);
    }

    @Override
    public Long resolveEncounterIdByServiceRequestId(Long serviceRequestId) {
        if (serviceRequestId == null) {
            return null;
        }
        return serviceRequestRepository.findById(serviceRequestId)
            .map(ServiceRequest::getEncounterId)
            .orElse(null);
    }

    private String resolveServiceRequestBusNo(Long serviceRequestId) {
        if (serviceRequestId == null) {
            return null;
        }
        return serviceRequestRepository.findById(serviceRequestId)
            .map(ServiceRequest::getBusNo)
            .orElse(null);
    }

    private boolean isBookableCategory(String categoryEnum) {
        return StringUtils.isNotBlank(categoryEnum) && BOOKABLE_CATEGORIES.contains(categoryEnum.trim());
    }
}
