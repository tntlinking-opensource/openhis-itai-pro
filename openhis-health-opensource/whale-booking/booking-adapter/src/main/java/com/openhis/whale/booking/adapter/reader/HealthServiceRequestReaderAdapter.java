package com.openhis.whale.booking.adapter.reader;

import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.booking.contracts.dto.BookableServiceRequestDto;
import com.openhis.whale.booking.contracts.reader.HealthServiceRequestReader;
import com.openhis.whale.booking.contracts.writer.HealthServiceRequestBookingWriter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@link HealthServiceRequestReader} 实现：医技预约读 health ServiceRequest。
 *
 * <p>仅暴露可预约类别（PROOF/TEST 等）且 status=active 的医嘱行。</p>
 */
@Component
@RequiredArgsConstructor
public class HealthServiceRequestReaderAdapter implements HealthServiceRequestReader, HealthServiceRequestBookingWriter {

    private static final Set<String> BOOKABLE_CATEGORIES = Set.of(
        "PROOF", "TEST", "ENDOSCOPY", "ORAL_EXAMINATION");

    private final ServiceRequestQueryService serviceRequestQueryService;

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
            // 仅 active 医嘱可发起医技预约。
            if (!RequestStatus.ACTIVE.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            BookableServiceRequestDto dto = new BookableServiceRequestDto();
            dto.setServiceRequestId(row.getId());
            dto.setServiceRequestBusNo(serviceRequestQueryService.resolveBusNoByServiceRequestId(row.getId()));
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
        return serviceRequestQueryService.resolveEncounterIdByServiceRequestId(serviceRequestId);
    }

    private boolean isBookableCategory(String categoryEnum) {
        return StringUtils.isNotBlank(categoryEnum) && BOOKABLE_CATEGORIES.contains(categoryEnum.trim());
    }
}
