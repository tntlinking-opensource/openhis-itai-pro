package com.openhis.whale.booking.application.outpatient.support;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.application.outpatient.mapper.OutpatientScheduleMapper;
import com.openhis.whale.booking.application.outpatient.response.GenerateFromPractitionerTemplateResponse;
import com.openhis.whale.booking.application.support.ScheduleActorMasterDataValidator;
import com.openhis.whale.booking.contracts.dto.PractitionerRoleScheduleItemDto;
import com.openhis.whale.booking.contracts.port.BookingPractitionerRolePort;
import com.openhis.whale.booking.domain.schedule.command.CreateScheduleCommand;
import com.openhis.whale.booking.domain.schedule.service.ScheduleDomainService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 从 health 岗位出诊模板生成 Schedule 草稿（2.10 / 2.22 共用）。
 */
@Service
@RequiredArgsConstructor
public class PractitionerScheduleGenerateSupport {

    private final BookingPractitionerRolePort bookingPractitionerRolePort;
    private final OutpatientScheduleMapper outpatientScheduleMapper;
    private final ScheduleActorMasterDataValidator scheduleActorMasterDataValidator;
    private final ScheduleDomainService scheduleDomainService;

    /**
     * 单医师岗位模板 → 多条 Schedule 草稿。
     */
    public GenerateFromPractitionerTemplateResponse generateForPractitioner(
        Long organizationId, Long practitionerId, Long operatorId) {
        List<PractitionerRoleScheduleItemDto> items = bookingPractitionerRolePort.listScheduleItemsByPractitioner(
            practitionerId, organizationId);
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "岗位出诊模板为空");
        }
        GenerateFromPractitionerTemplateResponse response = new GenerateFromPractitionerTemplateResponse();
        int index = 1;
        for (PractitionerRoleScheduleItemDto item : items) {
            String name = "岗位模板-" + practitionerId + "-" + index;
            CreateScheduleCommand command = outpatientScheduleMapper.toCreateScheduleFromPractitionerItem(
                item, organizationId, practitionerId, name, operatorId);
            scheduleActorMasterDataValidator.assertCreateCommandActorsReferenceValid(
                organizationId, command.getItem().getActors());
            response.getScheduleIds().add(scheduleDomainService.createSchedule(command));
            index++;
        }
        return response;
    }
}
