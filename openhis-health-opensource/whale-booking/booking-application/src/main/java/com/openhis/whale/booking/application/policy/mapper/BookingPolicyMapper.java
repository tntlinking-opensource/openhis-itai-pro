package com.openhis.whale.booking.application.policy.mapper;

import com.openhis.whale.booking.application.policy.request.SaveRegistrationPolicyRequest;
import com.openhis.whale.booking.application.policy.request.SaveReleasePolicyRequest;
import com.openhis.whale.booking.application.policy.request.SaveScheduleChangePolicyRequest;
import com.openhis.whale.booking.application.policy.response.GetRegistrationModeResponse;
import com.openhis.whale.booking.application.policy.response.RegistrationPolicyDto;
import com.openhis.whale.booking.application.policy.response.ReleasePolicyDto;
import com.openhis.whale.booking.application.policy.response.ScheduleChangePolicyDto;
import com.openhis.whale.booking.domain.policy.entity.RegistrationPolicy;
import com.openhis.whale.booking.domain.policy.entity.ReleasePolicy;
import com.openhis.whale.booking.domain.policy.entity.ScheduleChangePolicy;
import org.springframework.stereotype.Component;

@Component
public class BookingPolicyMapper {

    public ReleasePolicyDto toReleasePolicyDto(ReleasePolicy policy) {
        ReleasePolicyDto dto = new ReleasePolicyDto();
        dto.setId(policy.getId());
        dto.setOrganizationId(policy.getOrganizationId());
        dto.setPolicyCode(policy.getPolicyCode());
        dto.setPolicyName(policy.getPolicyName());
        dto.setAdvanceDays(policy.getAdvanceDays());
        dto.setSlotMinutes(policy.getSlotMinutes());
        dto.setCapacityPerSlot(policy.getCapacityPerSlot());
        dto.setChannelQuotaJson(policy.getChannelQuotaJson());
        return dto;
    }

    public ReleasePolicy toReleasePolicyEntity(SaveReleasePolicyRequest request) {
        ReleasePolicy policy = new ReleasePolicy();
        policy.setId(request.getId());
        policy.setOrganizationId(request.getOrganizationId());
        policy.setPolicyCode(request.getPolicyCode());
        policy.setPolicyName(request.getPolicyName());
        policy.setAdvanceDays(request.getAdvanceDays());
        policy.setSlotMinutes(request.getSlotMinutes());
        policy.setCapacityPerSlot(request.getCapacityPerSlot());
        policy.setChannelQuotaJson(request.getChannelQuotaJson());
        return policy;
    }

    public RegistrationPolicyDto toRegistrationPolicyDto(RegistrationPolicy policy) {
        RegistrationPolicyDto dto = new RegistrationPolicyDto();
        dto.setId(policy.getId());
        dto.setOrganizationId(policy.getOrganizationId());
        dto.setWalkInSlotMode(policy.getWalkInSlotMode());
        dto.setAutoCheckInOnRegister(policy.getAutoCheckInOnRegister());
        dto.setOnCancelPolicy(policy.getOnCancelPolicy());
        dto.setAllowEditPractitionerFromAppointment(policy.getAllowEditPractitionerFromAppointment());
        return dto;
    }

    public RegistrationPolicy toRegistrationPolicyEntity(SaveRegistrationPolicyRequest request) {
        RegistrationPolicy policy = new RegistrationPolicy();
        policy.setId(request.getId());
        policy.setOrganizationId(request.getOrganizationId());
        policy.setWalkInSlotMode(request.getWalkInSlotMode());
        policy.setAutoCheckInOnRegister(request.getAutoCheckInOnRegister());
        policy.setOnCancelPolicy(request.getOnCancelPolicy());
        policy.setAllowEditPractitionerFromAppointment(request.getAllowEditPractitionerFromAppointment());
        return policy;
    }

    public GetRegistrationModeResponse toGetRegistrationModeResponse(RegistrationPolicy policy) {
        GetRegistrationModeResponse response = new GetRegistrationModeResponse();
        response.setBookingEnabled(Boolean.TRUE);
        if (policy != null) {
            response.setWalkInSlotMode(policy.getWalkInSlotMode());
        } else {
            response.setWalkInSlotMode("APPOINTMENT_FIRST");
        }
        return response;
    }

    public ScheduleChangePolicyDto toScheduleChangePolicyDto(ScheduleChangePolicy policy) {
        return this.toScheduleChangePolicyDto(policy, policy.getId() != null);
    }

    public ScheduleChangePolicyDto toScheduleChangePolicyDto(ScheduleChangePolicy policy, boolean persisted) {
        ScheduleChangePolicyDto dto = new ScheduleChangePolicyDto();
        dto.setId(policy.getId());
        dto.setOrganizationId(policy.getOrganizationId());
        dto.setPersisted(persisted);
        dto.setOnPractitionerStopMode(policy.getOnPractitionerStopMode());
        dto.setTransferMatchRule(policy.getTransferMatchRule());
        dto.setTransferRequireSameService(policy.getTransferRequireSameService());
        dto.setTransferAllowCrossPractitioner(policy.getTransferAllowCrossPractitioner());
        dto.setTransferPatientConfirmMode(policy.getTransferPatientConfirmMode());
        dto.setBlockDeactivateIfUnhandled(policy.getBlockDeactivateIfUnhandled());
        dto.setNotifyOnTransfer(policy.getNotifyOnTransfer());
        dto.setNotifyOnCancelStop(policy.getNotifyOnCancelStop());
        dto.setFreezeSourceSlotOnPreview(policy.getFreezeSourceSlotOnPreview());
        dto.setFreezeTtlMinutes(policy.getFreezeTtlMinutes());
        return dto;
    }

    public ScheduleChangePolicy toScheduleChangePolicyEntity(SaveScheduleChangePolicyRequest request) {
        ScheduleChangePolicy policy = new ScheduleChangePolicy();
        policy.setId(request.getId());
        policy.setOrganizationId(request.getOrganizationId());
        policy.setOnPractitionerStopMode(request.getOnPractitionerStopMode());
        policy.setTransferMatchRule(request.getTransferMatchRule());
        policy.setTransferRequireSameService(request.getTransferRequireSameService());
        policy.setTransferAllowCrossPractitioner(request.getTransferAllowCrossPractitioner());
        policy.setTransferPatientConfirmMode(request.getTransferPatientConfirmMode());
        policy.setBlockDeactivateIfUnhandled(request.getBlockDeactivateIfUnhandled());
        policy.setNotifyOnTransfer(request.getNotifyOnTransfer());
        policy.setNotifyOnCancelStop(request.getNotifyOnCancelStop());
        policy.setFreezeSourceSlotOnPreview(request.getFreezeSourceSlotOnPreview());
        policy.setFreezeTtlMinutes(request.getFreezeTtlMinutes());
        return policy;
    }
}
