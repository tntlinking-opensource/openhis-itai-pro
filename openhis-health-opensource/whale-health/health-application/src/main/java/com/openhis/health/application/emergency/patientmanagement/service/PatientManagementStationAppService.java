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

package com.openhis.health.application.emergency.patientmanagement.service;

import com.openhis.health.application.common.pagination.PagedResultBuilder;
import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.emergency.emgtriage.station.request.PrintWristbandOrTriageSlipRequest;
import com.openhis.health.application.emergency.emgtriage.station.response.PrintWristbandOrTriageSlipResponse;
import com.openhis.health.application.emergency.emgtriage.station.service.EmergencyTriageStationAppService;
import com.openhis.health.application.emergency.patientmanagement.dto.BedLocationOptionDto;
import com.openhis.health.application.emergency.patientmanagement.dto.DestinationAreaOptionDto;
import com.openhis.health.application.emergency.patientmanagement.dto.EncounterCompanionRowDto;
import com.openhis.health.application.emergency.patientmanagement.dto.PatientBasicForEditDto;
import com.openhis.health.application.emergency.patientmanagement.dto.PatientFlowMilestoneDto;
import com.openhis.health.application.emergency.patientmanagement.dto.PatientAlertSummaryDto;
import com.openhis.health.application.emergency.patientmanagement.dto.PatientBlacklistRowDto;
import com.openhis.health.application.emergency.patientmanagement.dto.PatientDisplayFlagDto;
import com.openhis.health.application.emergency.patientmanagement.dto.PatientManagementListRowDto;
import com.openhis.health.application.emergency.patientmanagement.port.IdentityCapturePort;
import com.openhis.health.application.emergency.patientmanagement.port.IdentityCaptureResult;
import com.openhis.health.application.emergency.patientmanagement.port.WristbandPrintCommand;
import com.openhis.health.application.emergency.patientmanagement.port.WristbandPrintPort;
import com.openhis.health.application.emergency.patientmanagement.request.CaptureIdentityForPatientManagementRequest;
import com.openhis.health.application.emergency.patientmanagement.request.CreatePatientBlacklistRequest;
import com.openhis.health.application.emergency.patientmanagement.request.AdmitEmergencyPatientRequest;
import com.openhis.health.application.emergency.patientmanagement.request.DischargeFromEmergencyAreaRequest;
import com.openhis.health.application.emergency.patientmanagement.request.EndEmergencyVisitRequest;
import com.openhis.health.application.emergency.patientmanagement.request.GetActivePatientAlertsRequest;
import com.openhis.health.application.emergency.patientmanagement.request.ListEncounterCompanionsRequest;
import com.openhis.health.application.emergency.patientmanagement.request.GetPatientBasicForEditRequest;
import com.openhis.health.application.emergency.patientmanagement.request.GetPatientFocusRequest;
import com.openhis.health.application.emergency.patientmanagement.request.PagePatientBlacklistRequest;
import com.openhis.health.application.emergency.patientmanagement.request.ListBedsByAreaRequest;
import com.openhis.health.application.emergency.patientmanagement.request.ListPatientFlowTimelineRequest;
import com.openhis.health.application.emergency.patientmanagement.request.PagePatientsForManagementRequest;
import com.openhis.health.application.emergency.patientmanagement.request.PrintCompanionWristbandRequest;
import com.openhis.health.application.emergency.patientmanagement.request.RecordBedCardPrintRequest;
import com.openhis.health.application.emergency.patientmanagement.request.RevokeEncounterCompanionRequest;
import com.openhis.health.application.emergency.patientmanagement.request.SaveEncounterCompanionRequest;
import com.openhis.health.application.emergency.patientmanagement.request.RevokePatientBlacklistRequest;
import com.openhis.health.application.emergency.patientmanagement.request.SavePatientFocusRequest;
import com.openhis.health.application.emergency.patientmanagement.request.TransferEmergencyAreaRequest;
import com.openhis.health.application.emergency.patientmanagement.request.UpdatePatientBasicRequest;
import com.openhis.health.application.emergency.patientmanagement.response.CaptureIdentityForPatientManagementResponse;
import com.openhis.health.application.emergency.patientmanagement.response.CreatePatientBlacklistResponse;
import com.openhis.health.application.emergency.patientmanagement.response.FlowOperationResponse;
import com.openhis.health.application.emergency.patientmanagement.response.ListEncounterCompanionsResponse;
import com.openhis.health.application.emergency.patientmanagement.response.PrintCompanionWristbandResponse;
import com.openhis.health.application.emergency.patientmanagement.response.SaveEncounterCompanionResponse;
import com.openhis.health.application.emergency.patientmanagement.response.GetActivePatientAlertsResponse;
import com.openhis.health.application.emergency.patientmanagement.response.GetPatientBasicForEditResponse;
import com.openhis.health.application.emergency.patientmanagement.response.GetPatientFocusResponse;
import com.openhis.health.application.emergency.patientmanagement.response.ListBedsByAreaResponse;
import com.openhis.health.application.emergency.patientmanagement.response.ListDestinationAreasResponse;
import com.openhis.health.application.emergency.patientmanagement.response.ListPatientFlowTimelineResponse;
import com.openhis.health.application.emergency.patientmanagement.response.ListPatientDisplayFlagDefsResponse;
import com.openhis.health.application.emergency.patientmanagement.response.PagePatientBlacklistResponse;
import com.openhis.health.application.emergency.patientmanagement.response.PagePatientsForManagementResponse;
import com.openhis.health.application.emergency.patientmanagement.response.RecordBedCardPrintResponse;
import com.openhis.health.application.emergency.patientmanagement.response.SavePatientFocusResponse;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.patient.command.ModifyPatientProfileCommand;
import com.openhis.health.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientDetailQueryModel;
import com.openhis.health.domain.administration.patient.service.PatientDomainService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.custom.encounterhistory.queries.model.EncounterHistoryMilestoneQueryModel;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryDomainService;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryQueryService;
import com.openhis.health.domain.emergency.encountercompanion.command.RecordCompanionWristbandPrintCommand;
import com.openhis.health.domain.emergency.encountercompanion.command.RegisterEmgEncounterCompanionCommand;
import com.openhis.health.domain.emergency.encountercompanion.command.RevokeEmgEncounterCompanionCommand;
import com.openhis.health.domain.emergency.encountercompanion.command.UpdateEmgEncounterCompanionCommand;
import com.openhis.health.domain.emergency.encountercompanion.entity.EmgEncounterCompanion;
import com.openhis.health.domain.emergency.encountercompanion.queries.EmgEncounterCompanionQueryService;
import com.openhis.health.domain.emergency.encountercompanion.queries.model.EmgEncounterCompanionListItemQueryModel;
import com.openhis.health.domain.emergency.encountercompanion.service.EmgEncounterCompanionDomainService;
import com.openhis.health.domain.emergency.patientalert.command.CreatePatientAlertCommand;
import com.openhis.health.domain.emergency.patientalert.command.RevokePatientAlertCommand;
import com.openhis.health.domain.emergency.patientalert.entity.EmgPatientDisplayFlagDef;
import com.openhis.health.domain.emergency.patientalert.entity.PatientAlert;
import com.openhis.health.domain.emergency.patientalert.queries.PatientAlertQueryService;
import com.openhis.health.domain.emergency.patientalert.queries.model.PatientAlertActiveListItemQueryModel;
import com.openhis.health.domain.emergency.patientalert.service.PatientAlertDomainService;
import com.openhis.health.domain.emergency.patientfocus.command.SaveEmgPatientFocusCommand;
import com.openhis.health.domain.emergency.patientfocus.entity.EmgPatientFocus;
import com.openhis.health.domain.emergency.patientfocus.repository.EmgPatientFocusRepository;
import com.openhis.health.domain.emergency.patientfocus.service.EmgPatientFocusDomainService;
import com.openhis.health.application.emergency.patientmanagement.query.PatientBlacklistPageQuery;
import com.openhis.health.application.emergency.patientmanagement.query.PatientBlacklistQueryService;
import com.openhis.health.application.emergency.patientmanagement.query.PatientManagementPageQuery;
import com.openhis.health.application.emergency.patientmanagement.query.PatientManagementQueryService;
import com.openhis.health.application.emergency.patientmanagement.query.model.PatientManagementListItemQueryModel;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 患者管理工作站应用服务。
 */
@Service
@RequiredArgsConstructor
public class PatientManagementStationAppService {

    private final PatientManagementQueryService patientManagementQueryService;
    private final EmgPatientFocusDomainService emgPatientFocusDomainService;
    private final EmgPatientFocusRepository emgPatientFocusRepository;
    private final PatientManagementFlowAppService patientManagementFlowAppService;
    private final EncounterHistoryDomainService encounterHistoryDomainService;
    private final EncounterHistoryQueryService encounterHistoryQueryService;
    private final PatientQueryService patientQueryService;
    private final PatientDomainService patientDomainService;
    private final EmergencyTriageStationAppService emergencyTriageStationAppService;
    private final PatientAlertDomainService patientAlertDomainService;
    private final PatientAlertQueryService patientAlertQueryService;
    private final PatientBlacklistQueryService patientBlacklistQueryService;
    private final PatientDisplayFlagAssembler patientDisplayFlagAssembler;
    private final EmgEncounterCompanionDomainService emgEncounterCompanionDomainService;
    private final EmgEncounterCompanionQueryService emgEncounterCompanionQueryService;
    private final IdentityCapturePort identityCapturePort;
    private final WristbandPrintPort wristbandPrintPort;
    private final ICurrentUser currentUser;

    private final CurrentOperatorResolver currentOperatorResolver;

    public ListDestinationAreasResponse listDestinationAreas() {
        List<Location> locations = this.patientManagementQueryService.listDestinationAreas();
        ListDestinationAreasResponse resp = new ListDestinationAreasResponse();
        for (Location loc : locations) {
            DestinationAreaOptionDto dto = new DestinationAreaOptionDto();
            dto.setLocationId(loc.getId());
            dto.setDisplayName(loc.getName());
            resp.getAreas().add(dto);
        }
        return resp;
    }

    public PagePatientsForManagementResponse pagePatientsForManagement(PagePatientsForManagementRequest request) {
        validatePageRequest(request);
        PatientManagementPageQuery q = new PatientManagementPageQuery();
        q.setPageNo(request.getPageNo());
        q.setPageSize(request.getPageSize());
        q.setLocationId(request.getLocationId());
        q.setRegistrationTimeFilter(StringUtils.defaultIfBlank(request.getRegistrationTimeFilter(), "ALL"));
        q.setExactSearch(Boolean.TRUE.equals(request.getExactSearch()));
        q.setPatientId(request.getPatientId());
        q.setPatientName(request.getPatientName());
        q.setBedLabel(request.getBedLabel());
        q.setKeyword(request.getKeyword());

        Page<PatientManagementListItemQueryModel> page = this.patientManagementQueryService.pagePatients(q);
        List<EmgPatientDisplayFlagDef> flagDefs = patientDisplayFlagAssembler.loadEnabledDefinitions();
        List<PatientManagementListRowDto> rows = new ArrayList<>();
        long waitSum = 0;
        int waitCount = 0;
        for (PatientManagementListItemQueryModel item : page.getContent()) {
            rows.add(toRowDto(item, flagDefs));
            waitSum += item.getWaitMinutes();
            waitCount++;
        }
        PagePatientsForManagementResponse resp =
            PagedResultBuilder.build(new PagePatientsForManagementResponse(), rows, page);
        if (waitCount > 0) {
            long avg = waitSum / waitCount;
            resp.setAverageWaitMinutes(avg);
            int over = 0;
            for (PatientManagementListRowDto row : rows) {
                if (row.getWaitMinutes() != null && row.getWaitMinutes() > avg) {
                    over++;
                }
            }
            resp.setOverAverageWaitCount(over);
        }
        return resp;
    }

    public SavePatientFocusResponse savePatientFocus(SavePatientFocusRequest request) {
        if (request == null || request.getEncounterId() == null || request.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "请求体不完整");
        }
        SaveEmgPatientFocusCommand cmd = new SaveEmgPatientFocusCommand();
        cmd.setEncounterId(request.getEncounterId());
        cmd.setPatientId(request.getPatientId());
        cmd.setFocusNote(request.getFocusNote());
        cmd.setRevoke(Boolean.TRUE.equals(request.getRevoke()));
        cmd.setOperatorId(this.requireOperatorId());
        EmgPatientFocus saved = this.emgPatientFocusDomainService.savePatientFocus(cmd);
        SavePatientFocusResponse resp = new SavePatientFocusResponse();
        resp.setFocused(EmgPatientFocus.STATUS_ACTIVE.equals(saved.getStatusEnum()));
        resp.setFocusNote(saved.getFocusNote());
        return resp;
    }

    public GetPatientFocusResponse getPatientFocus(GetPatientFocusRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        Optional<EmgPatientFocus> row = this.emgPatientFocusRepository.findByEncounterIdAndStatusEnumAndIsDeletedFalse(
            request.getEncounterId(), EmgPatientFocus.STATUS_ACTIVE);
        GetPatientFocusResponse resp = new GetPatientFocusResponse();
        if (row.isPresent()) {
            resp.setFocused(true);
            resp.setFocusNote(row.get().getFocusNote());
        } else {
            resp.setFocused(false);
        }
        return resp;
    }

    public GetPatientBasicForEditResponse getPatientBasicForEdit(GetPatientBasicForEditRequest request) {
        if (request == null || request.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "患者标识不能为空");
        }
        PatientDetailQueryModel detail = patientQueryService.loadPatientDetail(request.getPatientId());
        if (detail == null || detail.getPatient() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_NOT_FOUND, "患者不存在");
        }
        GetPatientBasicForEditResponse resp = new GetPatientBasicForEditResponse();
        resp.setPatient(toBasicDto(detail.getPatient()));
        return resp;
    }

    public GetPatientBasicForEditResponse updatePatientBasic(UpdatePatientBasicRequest request) {
        if (request == null || request.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "患者标识不能为空");
        }
        PatientDetailQueryModel detail = patientQueryService.loadPatientDetail(request.getPatientId());
        if (detail == null || detail.getPatient() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_NOT_FOUND, "患者不存在");
        }
        PatientCoreQueryModel existing = detail.getPatient();
        ModifyPatientProfileCommand cmd = new ModifyPatientProfileCommand();
        ModifyPatientProfileCommand.ModifyPatientProfileItemCommand item =
            new ModifyPatientProfileCommand.ModifyPatientProfileItemCommand();
        item.setPatientId(request.getPatientId());
        item.setName(StringUtils.defaultIfBlank(request.getName(), existing.getName()));
        item.setGenderEnum(StringUtils.defaultIfBlank(request.getGenderEnum(), existing.getGenderEnum()));
        if (request.getBirthDate() != null) {
            item.setBirthDate(request.getBirthDate());
        } else {
            item.setBirthDate(existing.getBirthDate());
        }
        item.setIdCard(StringUtils.defaultIfBlank(request.getIdCard(), existing.getIdCard()));
        item.setPhone(StringUtils.defaultIfBlank(request.getPhone(), existing.getPhone()));
        item.setAddress(StringUtils.defaultIfBlank(request.getAddress(), existing.getAddress()));
        cmd.setItems(List.of(item));
        patientDomainService.modifyPatientProfile(cmd);
        GetPatientBasicForEditRequest reload = new GetPatientBasicForEditRequest();
        reload.setPatientId(request.getPatientId());
        return getPatientBasicForEdit(reload);
    }

    public ListBedsByAreaResponse listBedsByArea(ListBedsByAreaRequest request) {
        ListBedsByAreaResponse resp = new ListBedsByAreaResponse();
        if (request == null || request.getAreaLocationId() == null) {
            return resp;
        }
        for (Location bed : patientManagementQueryService.listBedsByAreaLocation(request.getAreaLocationId())) {
            BedLocationOptionDto dto = new BedLocationOptionDto();
            dto.setLocationId(bed.getId());
            dto.setDisplayName(StringUtils.defaultIfBlank(bed.getName(), bed.getBusNo()));
            resp.getBeds().add(dto);
        }
        return resp;
    }

    public FlowOperationResponse admitEmergencyPatient(AdmitEmergencyPatientRequest request) {
        Long encounterId = null;
        Long emgTriageId = null;
        if (request != null) {
            encounterId = request.getEncounterId();
            emgTriageId = request.getEmgTriageId();
        }
        requireFlowRequest(encounterId, emgTriageId);
        patientManagementFlowAppService.admitEmergencyPatient(request, this.requireOperatorId());
        return new FlowOperationResponse().setEncounterId(request.getEncounterId()).setMessage("入科成功");
    }

    public FlowOperationResponse transferEmergencyArea(TransferEmergencyAreaRequest request) {
        Long encounterId = null;
        Long emgTriageId = null;
        if (request != null) {
            encounterId = request.getEncounterId();
            emgTriageId = request.getEmgTriageId();
        }
        requireFlowRequest(encounterId, emgTriageId);
        patientManagementFlowAppService.transferEmergencyArea(request, this.requireOperatorId());
        return new FlowOperationResponse().setEncounterId(request.getEncounterId()).setMessage("转区成功");
    }

    public FlowOperationResponse dischargeFromEmergencyArea(DischargeFromEmergencyAreaRequest request) {
        Long encounterId = null;
        Long emgTriageId = null;
        if (request != null) {
            encounterId = request.getEncounterId();
            emgTriageId = request.getEmgTriageId();
        }
        requireFlowRequest(encounterId, emgTriageId);
        patientManagementFlowAppService.dischargeFromEmergencyArea(request, this.requireOperatorId());
        return new FlowOperationResponse().setEncounterId(request.getEncounterId()).setMessage("出科成功");
    }

    public FlowOperationResponse endEmergencyVisit(EndEmergencyVisitRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        patientManagementFlowAppService.endEmergencyVisit(request, this.requireOperatorId());
        return new FlowOperationResponse().setEncounterId(request.getEncounterId()).setMessage("结束就诊成功");
    }

    public ListPatientFlowTimelineResponse listPatientFlowTimeline(ListPatientFlowTimelineRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        ListPatientFlowTimelineResponse resp = new ListPatientFlowTimelineResponse();
        for (EncounterHistoryMilestoneQueryModel row :
            encounterHistoryQueryService.listMilestonesByEncounter(request.getEncounterId())) {
            PatientFlowMilestoneDto dto = new PatientFlowMilestoneDto();
            dto.setMilestoneType(row.getName());
            dto.setDisplayTitle(formatMilestoneTitle(row.getName()));
            dto.setOccurredAt(row.getCreatedAt());
            dto.setDetailText(formatMilestoneDetail(row));
            resp.getMilestones().add(dto);
        }
        return resp;
    }

    public PrintWristbandOrTriageSlipResponse printWristbandForManagement(PrintWristbandOrTriageSlipRequest request) {
        return emergencyTriageStationAppService.printWristbandOrTriageSlip(request);
    }

    public RecordBedCardPrintResponse recordBedCardPrint(RecordBedCardPrintRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        Long milestoneId = encounterHistoryDomainService.recordBedCardPrintMilestone(
            request.getEncounterId(), request.getBedId(), this.requireOperatorId());
        RecordBedCardPrintResponse resp = new RecordBedCardPrintResponse();
        resp.setMilestoneId(milestoneId);
        resp.setMessage("床卡打印已记录");
        return resp;
    }

    private void requireFlowRequest(Long encounterId, Long emgTriageId) {
        if (encounterId == null || emgTriageId == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "就诊或分诊标识不能为空");
        }
    }

    private static PatientBasicForEditDto toBasicDto(PatientCoreQueryModel patient) {
        PatientBasicForEditDto dto = new PatientBasicForEditDto();
        dto.setPatientId(patient.getId());
        dto.setName(patient.getName());
        dto.setGenderEnum(patient.getGenderEnum());
        AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, patient.getGenderEnum());
        if (gender != null) {
            dto.setGenderDisplay(gender.getDisplay());
        }
        dto.setBirthDate(patient.getBirthDate());
        dto.setIdCard(patient.getIdCard());
        dto.setPhone(patient.getPhone());
        dto.setAddress(patient.getAddress());
        return dto;
    }

    private static String formatMilestoneTitle(String name) {
        if (name == null) {
            return "流转";
        }
        return switch (name) {
            case CustomConstants.EncounterHistoryMilestoneName.ADMISSION_TO_DEPT -> "入科";
            case CustomConstants.EncounterHistoryMilestoneName.TRANSFER -> "转区";
            case CustomConstants.EncounterHistoryMilestoneName.DISCHARGE -> "出科";
            case CustomConstants.EncounterHistoryMilestoneName.BED_CARD_PRINT -> "床卡打印";
            case CustomConstants.EncounterHistoryMilestoneName.COMPANION_WRISTBAND_PRINT -> "陪护腕带打印";
            default -> name;
        };
    }

    private static String formatMilestoneDetail(EncounterHistoryMilestoneQueryModel row) {
        List<String> parts = new ArrayList<>();
        if (row.getOriginalLocationId() != null) {
            parts.add("原位置 " + row.getOriginalLocationId());
        }
        if (row.getTargetLocationId() != null) {
            parts.add("目标位置 " + row.getTargetLocationId());
        }
        if (StringUtils.isNotBlank(row.getOutWayCode())) {
            parts.add("方式 " + row.getOutWayCode());
        }
        if (parts.isEmpty()) {
            return "";
        }
        return String.join(" · ", parts);
    }

    private void validatePageRequest(AbstractPageQuery request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "请求体不能为空");
        }
        if (request.getPageNo() == null || request.getPageNo() < 1) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "页码须从 1 开始");
        }
        if (request.getPageSize() == null || request.getPageSize() < 1) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "页大小须为正整数");
        }
    }

    public CreatePatientBlacklistResponse createPatientBlacklist(CreatePatientBlacklistRequest request) {
        if (request == null || request.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "患者标识不能为空");
        }
        if (StringUtils.isBlank(request.getReasonText())) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "黑名单原因不能为空");
        }
        CreatePatientAlertCommand cmd = new CreatePatientAlertCommand();
        cmd.setPatientId(request.getPatientId());
        cmd.setAlertTypeEnum(PatientAlert.TYPE_BLACKLIST);
        cmd.setMarkTypeCode(StringUtils.trimToNull(request.getMarkTypeCode()));
        cmd.setReasonText(request.getReasonText().trim());
        cmd.setCreatedBy(this.requireOperatorId());
        Long alertId = patientAlertDomainService.createPatientAlert(cmd);
        return new CreatePatientBlacklistResponse().setAlertId(alertId).setMessage("已加入黑名单");
    }

    public FlowOperationResponse revokePatientBlacklist(RevokePatientBlacklistRequest request) {
        if (request == null || request.getAlertId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "警示标识不能为空");
        }
        RevokePatientAlertCommand cmd = new RevokePatientAlertCommand();
        cmd.setAlertId(request.getAlertId());
        cmd.setRevokedBy(this.requireOperatorId());
        patientAlertDomainService.revokePatientAlert(cmd);
        return new FlowOperationResponse().setMessage("已移出黑名单");
    }

    public PagePatientBlacklistResponse pagePatientBlacklist(PagePatientBlacklistRequest request) {
        validatePageRequest(request);
        PatientBlacklistPageQuery q = new PatientBlacklistPageQuery();
        q.setPageNo(request.getPageNo());
        q.setPageSize(request.getPageSize());
        q.setPatientName(request.getPatientName());
        q.setMarkTypeCode(request.getMarkTypeCode());
        Page<PatientBlacklistRowDto> page = patientBlacklistQueryService.pageBlacklist(q);
        return PagedResultBuilder.build(new PagePatientBlacklistResponse(), page.getContent(), page);
    }

    public GetActivePatientAlertsResponse getActivePatientAlerts(GetActivePatientAlertsRequest request) {
        if (request == null || request.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "患者标识不能为空");
        }
        GetActivePatientAlertsResponse resp = new GetActivePatientAlertsResponse();
        for (PatientAlertActiveListItemQueryModel alert :
            patientAlertQueryService.listActiveAlertsByPatient(request.getPatientId(), null)) {
            PatientAlertSummaryDto dto = new PatientAlertSummaryDto();
            dto.setAlertId(alert.getId());
            dto.setPatientId(alert.getPatientId());
            dto.setAlertTypeEnum(alert.getAlertTypeEnum());
            dto.setMarkTypeCode(alert.getMarkTypeCode());
            dto.setMarkTypeDisplay(PatientDisplayFlagAssembler.markTypeDisplay(alert.getMarkTypeCode()));
            dto.setReasonText(alert.getReasonText());
            resp.getAlerts().add(dto);
        }
        return resp;
    }

    public ListPatientDisplayFlagDefsResponse listPatientDisplayFlagDefs() {
        ListPatientDisplayFlagDefsResponse resp = new ListPatientDisplayFlagDefsResponse();
        for (EmgPatientDisplayFlagDef def : patientDisplayFlagAssembler.loadEnabledDefinitions()) {
            resp.getFlags().add(new PatientDisplayFlagDto()
                .setFlagCode(def.getFlagCode())
                .setDisplayLabel(def.getDisplayLabel())
                .setSeverity(def.getSeverity()));
        }
        return resp;
    }

    public ListEncounterCompanionsResponse listCompanionsByEncounter(ListEncounterCompanionsRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        ListEncounterCompanionsResponse resp = new ListEncounterCompanionsResponse();
        for (EmgEncounterCompanionListItemQueryModel item :
            emgEncounterCompanionQueryService.listActiveCompanionsByEncounter(request.getEncounterId())) {
            resp.getCompanions().add(toCompanionDto(item));
        }
        return resp;
    }

    public SaveEncounterCompanionResponse saveEncounterCompanion(SaveEncounterCompanionRequest request) {
        if (request == null || request.getEncounterId() == null || request.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "就诊与患者标识不能为空");
        }
        if (StringUtils.isBlank(request.getName())) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "陪护人姓名不能为空");
        }
        String idCardSource = StringUtils.defaultIfBlank(
            request.getIdCardSourceEnum(), EmgEncounterCompanion.SOURCE_MANUAL);

        EmgEncounterCompanion saved;
        Long companionId = request.getCompanionId();
        if (companionId == null) {
            RegisterEmgEncounterCompanionCommand cmd = new RegisterEmgEncounterCompanionCommand();
            cmd.setEncounterId(request.getEncounterId());
            cmd.setPatientId(request.getPatientId());
            cmd.setName(request.getName().trim());
            cmd.setIdCard(StringUtils.trimToNull(request.getIdCard()));
            cmd.setGenderEnum(StringUtils.trimToNull(request.getGenderEnum()));
            cmd.setPhone(StringUtils.trimToNull(request.getPhone()));
            cmd.setRelationshipCode(StringUtils.trimToNull(request.getRelationshipCode()));
            cmd.setIdCardSourceEnum(idCardSource);
            cmd.setRemark(StringUtils.trimToNull(request.getRemark()));
            saved = emgEncounterCompanionDomainService.registerCompanion(cmd);
            companionId = saved.getId();
        } else {
            UpdateEmgEncounterCompanionCommand cmd = new UpdateEmgEncounterCompanionCommand();
            cmd.setCompanionId(companionId);
            cmd.setName(request.getName().trim());
            cmd.setIdCard(StringUtils.trimToNull(request.getIdCard()));
            cmd.setGenderEnum(StringUtils.trimToNull(request.getGenderEnum()));
            cmd.setPhone(StringUtils.trimToNull(request.getPhone()));
            cmd.setRelationshipCode(StringUtils.trimToNull(request.getRelationshipCode()));
            cmd.setIdCardSourceEnum(idCardSource);
            cmd.setRemark(StringUtils.trimToNull(request.getRemark()));
            saved = emgEncounterCompanionDomainService.updateCompanion(cmd);
        }

        EmgEncounterCompanionListItemQueryModel model =
            emgEncounterCompanionQueryService.findActiveById(companionId);
        EncounterCompanionRowDto companionDto;
        if (model != null) {
            companionDto = toCompanionDto(model);
        } else {
            companionDto = toCompanionDto(saved);
        }
        String saveMessage;
        if (request.getCompanionId() == null) {
            saveMessage = "陪护人已登记";
        } else {
            saveMessage = "陪护信息已更新";
        }
        SaveEncounterCompanionResponse resp = new SaveEncounterCompanionResponse()
            .setCompanionId(companionId)
            .setCompanion(companionDto)
            .setMessage(saveMessage);

        applyDuplicateIdCardWarning(resp, request.getEncounterId(), request.getIdCard(), companionId);
        return resp;
    }

    public FlowOperationResponse revokeEncounterCompanion(RevokeEncounterCompanionRequest request) {
        if (request == null || request.getCompanionId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "陪护标识不能为空");
        }
        RevokeEmgEncounterCompanionCommand cmd = new RevokeEmgEncounterCompanionCommand();
        cmd.setCompanionId(request.getCompanionId());
        emgEncounterCompanionDomainService.revokeCompanion(cmd);
        return new FlowOperationResponse().setMessage("陪护人已注销");
    }

    public PrintCompanionWristbandResponse printCompanionWristband(PrintCompanionWristbandRequest request) {
        if (request == null || request.getCompanionId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "陪护标识不能为空");
        }
        EmgEncounterCompanionListItemQueryModel companion =
            emgEncounterCompanionQueryService.findActiveById(request.getCompanionId());
        if (companion == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_NOT_FOUND, "陪护记录不存在");
        }
        Long encounterId;
        if (request.getEncounterId() != null) {
            encounterId = request.getEncounterId();
        } else {
            encounterId = companion.getEncounterId();
        }

        String patientName = "";
        PatientDetailQueryModel detail = patientQueryService.loadPatientDetail(companion.getPatientId());
        if (detail != null && detail.getPatient() != null) {
            patientName = StringUtils.defaultString(detail.getPatient().getName());
        }

        WristbandPrintCommand printCmd = new WristbandPrintCommand()
            .setSubjectType(WristbandPrintCommand.SUBJECT_COMPANION)
            .setEncounterId(encounterId)
            .setPatientId(companion.getPatientId())
            .setCompanionId(companion.getId())
            .setCompanionName(companion.getName())
            .setPatientName(patientName);

        if (!wristbandPrintPort.print(printCmd)) {
            return new PrintCompanionWristbandResponse()
                .setPrintSucceeded(false)
                .setMessage("腕带打印失败，未回写打印时间");
        }

        LocalDateTime printedAt = LocalDateTime.now();
        RecordCompanionWristbandPrintCommand recordCmd = new RecordCompanionWristbandPrintCommand();
        recordCmd.setCompanionId(companion.getId());
        recordCmd.setWristbandPrintedAt(printedAt);
        emgEncounterCompanionDomainService.recordCompanionWristbandPrint(recordCmd);
        encounterHistoryDomainService.recordCompanionWristbandPrintMilestone(encounterId, companion.getId());

        return new PrintCompanionWristbandResponse()
            .setPrintSucceeded(true)
            .setWristbandPrintedAt(printedAt)
            .setMessage("陪护腕带已打印");
    }

    public CaptureIdentityForPatientManagementResponse captureIdentityForPatientManagement(
        CaptureIdentityForPatientManagementRequest request) {
        String channel = "";
        String raw = "";
        if (request != null) {
            channel = StringUtils.defaultString(request.getChannelCode());
            raw = request.getRawPayload();
        }
        IdentityCaptureResult normalized = identityCapturePort.normalize(channel, raw);
        return new CaptureIdentityForPatientManagementResponse()
            .setNormalizedIdCard(normalized.getNormalizedIdCard())
            .setName(normalized.getName())
            .setGenderEnum(normalized.getGenderEnum())
            .setAddress(normalized.getAddress())
            .setHintMessage(normalized.getHintMessage());
    }

    private void applyDuplicateIdCardWarning(
        SaveEncounterCompanionResponse resp,
        Long encounterId,
        String idCard,
        Long companionId) {
        if (StringUtils.isBlank(idCard)) {
            return;
        }
        if (emgEncounterCompanionQueryService.hasActiveDuplicateIdCard(encounterId, idCard, companionId)) {
            resp.setDuplicateIdCardWarning(true);
            resp.setDuplicateIdCardMessage("本就诊下已有其他陪护使用相同身份证号，请核对是否为同一人");
        }
    }

    private static EncounterCompanionRowDto toCompanionDto(EmgEncounterCompanionListItemQueryModel item) {
        EncounterCompanionRowDto dto = new EncounterCompanionRowDto();
        dto.setCompanionId(item.getId());
        dto.setEncounterId(item.getEncounterId());
        dto.setPatientId(item.getPatientId());
        dto.setName(item.getName());
        dto.setIdCard(item.getIdCard());
        dto.setGenderEnum(item.getGenderEnum());
        dto.setPhone(item.getPhone());
        dto.setRelationshipCode(item.getRelationshipCode());
        dto.setIdCardSourceEnum(item.getIdCardSourceEnum());
        dto.setWristbandPrintedAt(item.getWristbandPrintedAt());
        dto.setRemark(item.getRemark());
        dto.setCreatedAt(item.getCreatedAt());
        return dto;
    }

    private static EncounterCompanionRowDto toCompanionDto(EmgEncounterCompanion entity) {
        EncounterCompanionRowDto dto = new EncounterCompanionRowDto();
        dto.setCompanionId(entity.getId());
        dto.setEncounterId(entity.getEncounterId());
        dto.setPatientId(entity.getPatientId());
        dto.setName(entity.getName());
        dto.setIdCard(entity.getIdCard());
        dto.setGenderEnum(entity.getGenderEnum());
        dto.setPhone(entity.getPhone());
        dto.setRelationshipCode(entity.getRelationshipCode());
        dto.setIdCardSourceEnum(entity.getIdCardSourceEnum());
        dto.setWristbandPrintedAt(entity.getWristbandPrintedAt());
        dto.setRemark(entity.getRemark());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private PatientManagementListRowDto toRowDto(
        PatientManagementListItemQueryModel item,
        List<EmgPatientDisplayFlagDef> flagDefs) {
        PatientManagementListRowDto dto = new PatientManagementListRowDto();
        dto.setEncounterId(item.getEncounterId());
        dto.setPatientId(item.getPatientId());
        dto.setEmgTriageId(item.getEmgTriageId());
        dto.setLocationId(item.getLocationId());
        dto.setPatientName(StringUtils.defaultString(item.getPatientName()));
        dto.setBedLabel(StringUtils.defaultIfBlank(item.getBedLabel(), "—"));
        dto.setAreaName(StringUtils.defaultString(item.getAreaName()));
        dto.setTriageLevelDisplay(formatTriageLevel(item.getTriageLevelEnum()));
        dto.setNursingLevelDisplay(StringUtils.defaultIfBlank(item.getNursingLevelDisplay(), "—"));
        if (item.getWaitMinutes() > 0) {
            dto.setWaitDisplay("等候 " + item.getWaitMinutes() + "′");
        } else {
            dto.setWaitDisplay("—");
        }
        dto.setFocused(item.isFocused());
        dto.setBlacklisted(item.isBlacklisted());
        dto.setFocusNote(item.getFocusNote());
        dto.setRegistrationTime(item.getRegistrationTime());
        dto.setWaitMinutes(item.getWaitMinutes());
        dto.setEncounterBusNo(item.getEncounterBusNo());

        String gender = StringUtils.defaultString(item.getGenderDisplay());
        String age = "";
        if (item.getAgeYears() != null && item.getAgeYears() > 0) {
            age = item.getAgeYears() + "岁";
        }
        dto.setGenderAge(String.join(" · ", Stream.of(gender, age).filter(StringUtils::isNotBlank).toList()));

        List<PatientDisplayFlagDto> displayFlags =
            patientDisplayFlagAssembler.assemble(flagDefs, item, item.isBlacklisted());
        dto.setDisplayFlags(displayFlags);
        if (displayFlags.isEmpty()) {
            dto.setFlagSummary("");
        } else {
            dto.setFlagSummary(String.join(
                "·", displayFlags.stream().map(PatientDisplayFlagDto::getDisplayLabel).toList()));
        }
        return dto;
    }

    /**
     * 写路径操作人门控（B.6.1）。
     *
     * @return 当前登录用户主键
     */
    private Long requireOperatorId() {
        return this.currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT,
            "当前会话操作人不能为空");
    }

    private static String formatTriageLevel(Integer level) {
        if (level == null) {
            return "—";
        }
        return switch (level) {
            case 1 -> "Ⅰ级";
            case 2 -> "Ⅱ级";
            case 3 -> "Ⅲ级";
            case 4 -> "Ⅳ级";
            case 5 -> "Ⅴ级";
            default -> level + "级";
        };
    }
}
