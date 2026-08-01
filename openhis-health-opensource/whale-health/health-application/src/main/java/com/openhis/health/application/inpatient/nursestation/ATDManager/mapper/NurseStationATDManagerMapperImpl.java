/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ATDManager.mapper;

import static com.openhis.whale.core.application.util.StringUtils.firstNonBlank;

import com.openhis.health.application.inpatient.nursestation.ATDManager.response.*;
import com.openhis.health.application.shared.inpatient.AdmissionPrimaryDiagnosisAssembler;
import com.openhis.health.application.shared.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.BedBoardItemDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.BedBoardLocationBlockDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.BedBoardOccupantDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.DischargePendingExecutionItemDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.DischargePendingMedicationItemDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ClearDischargedBedRequest;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ExecuteBedOperationRequest;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ExecuteInpatientDischargeRequest;
import com.openhis.health.domain.administration.encounter.command.ClearDischargedBedCommand;
import com.openhis.health.domain.administration.encounter.command.ExecuteInpatientNurseDischargeCommand;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;

import java.time.LocalDateTime;
import java.util.List;
import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.ParticipantSubmitDto;
import com.openhis.health.domain.administration.encounter.command.AssignInpatientBedCommand;
import com.openhis.health.domain.administration.encounter.command.ConfirmBedTransferCommand;
import com.openhis.health.domain.administration.encounter.command.SwapInpatientBedsCommand;
import com.openhis.health.domain.administration.location.command.OccupyBedCommand;
import com.openhis.health.domain.administration.location.command.ReleaseBedCommand;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.utils.AdministrativeGenderDisplayUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link NurseStationATDManagerMapper} 实现（AS-IP-NUR-002-SDD §3.2～3.3）。
 *
 * <p>职责：Request/Command/Response 字段搬运与诊断展示回落；禁止业务流程判断与 Gate/Pre。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationATDManagerMapperImpl implements NurseStationATDManagerMapper {

    private final AdmissionPrimaryDiagnosisAssembler admissionPrimaryDiagnosisAssembler;

    /**
     * 床位列表行 → 列表响应。
     *
     * @param items 已装配列表项；调用方保证非 null
     * @return 列表响应
     */
    @Override
    public ListBedBoardResponse toListBedBoardResponse(List<BedBoardItemDto> items) {
        ListBedBoardResponse response = new ListBedBoardResponse();
        if (items == null) {
            response.setItems(Collections.emptyList());
            return response;
        }
        response.setItems(items);
        return response;
    }

    /**
     * 床位主数据 + 病房展示名 → 位置块 DTO（BR-IP-ATD-001）。
     *
     * @param bed       床位 Location；调用方保证非 null
     * @param houseName 所属病房名称；可 null
     * @return 位置块；bed 为 null 时返回 null
     */
    @Override
    public BedBoardLocationBlockDto toBedBoardLocationBlock(Location bed, String houseName) {
        if (bed == null) {
            return null;
        }
        BedBoardLocationBlockDto block = new BedBoardLocationBlockDto();
        block.setBedId(bed.getId());
        block.setBedName(bed.getName());
        block.setOperationalEnum(bed.getOperationalEnum());
        block.setDisplayOrder(bed.getDisplayOrder());
        if (bed.getParentId() != null) {
            block.setHouseId(bed.getParentId());
        }
        block.setHouseName(houseName);
        return block;
    }

    /**
     * 读模型行 + 展示覆盖 + 余额 → 占床患者块。
     *
     * @param row               住院信息读模型；调用方保证非 null
     * @param overlay           队列展示覆盖；可 null
     * @param accountBalance    展示余额；可 null
     * @param lowBalanceWarning 低余额预警
     * @param priorityEnum      病情 code；可 null
     * @return 占床块；row 为 null 时返回 null
     */
    @Override
    public BedBoardOccupantDto toBedBoardOccupantDto(
        InpatientEncounterContextQueryModel row,
        InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay overlay,
        BigDecimal accountBalance,
        boolean lowBalanceWarning,
        String priorityEnum) {
        if (row == null) {
            return null;
        }
        String overlayBusNo = null;
        String overlayPatientName = null;
        String overlayStatusText = null;
        if (overlay != null) {
            overlayBusNo = overlay.getEncounterBusNo();
            overlayPatientName = overlay.getPatientName();
            overlayStatusText = overlay.getStatusEnumText();
        }
        BedBoardOccupantDto dto = new BedBoardOccupantDto();
        dto.setEncounterId(row.getEncounterId());
        dto.setPatientId(row.getPatientId());
        dto.setPatientName(firstNonBlank(row.getPatientName(), overlayPatientName));
        String genderCode = row.getGenderEnum();
        if (overlay != null) {
            genderCode = firstNonBlank(genderCode, overlay.getGenderEnum());
        }
        dto.setGenderEnum(genderCode);
        dto.setGenderEnumText(AdministrativeGenderDisplayUtil.toDisplayText(genderCode));
        dto.setAgeText(row.getAgeText());
        dto.setEncounterBusNo(firstNonBlank(row.getEncounterBusNo(), overlayBusNo));
        dto.setStatusEnum(row.getStatusEnum());
        dto.setStatusEnumText(firstNonBlank(row.getStatusEnumText(), overlayStatusText));
        dto.setPriorityEnum(priorityEnum);
        dto.setAdmissionTime(row.getStartTime());
        dto.setAttendingDoctorName(row.getAttendingDoctorName());
        dto.setPrimaryNurseName(row.getPrimaryNurseName());
        List<String> diagnosisLines =
            admissionPrimaryDiagnosisAssembler.resolveDiagnosisDisplayLines(row.getDiagnosisJson());
        dto.setDiagnosisSummary(
            admissionPrimaryDiagnosisAssembler.joinDiagnosisDisplayLines(diagnosisLines));
        dto.setAccountBalance(accountBalance);
        dto.setLowBalanceWarning(lowBalanceWarning);
        return dto;
    }

    /**
     * 分床请求 → {@link AssignInpatientBedCommand}。
     *
     * @param request    写请求；调用方保证非 null
     * @param operatorId 操作人主键
     * @return 领域分床命令
     */
    @Override
    public AssignInpatientBedCommand toAssignInpatientBedCommand(ExecuteBedOperationRequest request, Long operatorId) {
        AssignInpatientBedCommand command = new AssignInpatientBedCommand();
        command.setOperatorId(operatorId);
        AssignInpatientBedCommand.AssignInpatientBedItemCommand item =
            new AssignInpatientBedCommand.AssignInpatientBedItemCommand();
        item.setEncounterId(request.getEncounterId());
        item.setHouseId(request.getHouseLocationId());
        item.setBedId(request.getBedLocationId());
        item.setPriorityEnum(request.getPriorityEnum());
        List<AssignInpatientBedCommand.ParticipantLine> lines = new ArrayList<>();
        if (request.getParticipants() != null) {
            for (ParticipantSubmitDto row : request.getParticipants()) {
                if (row == null) {
                    continue;
                }
                AssignInpatientBedCommand.ParticipantLine line = new AssignInpatientBedCommand.ParticipantLine();
                line.setRoleCode(row.getRoleCode());
                line.setPractitionerId(row.getPractitionerId());
                lines.add(line);
            }
        }
        item.setParticipants(lines);
        command.setItem(item);
        return command;
    }

    /**
     * 换床请求 + 原床 id → {@link ConfirmBedTransferCommand}。
     *
     * @param request        写请求；调用方保证非 null
     * @param fromLocationId 当前活动床 locationId
     * @param operatorId     操作人主键
     * @return 领域转床命令
     */
    @Override
    public ConfirmBedTransferCommand toConfirmBedTransferCommand(
        ExecuteBedOperationRequest request,
        Long fromLocationId,
        Long operatorId) {
        ConfirmBedTransferCommand command = new ConfirmBedTransferCommand();
        command.setOperatorId(operatorId);
        ConfirmBedTransferCommand.ConfirmBedTransferItemCommand item =
            new ConfirmBedTransferCommand.ConfirmBedTransferItemCommand();
        item.setEncounterId(request.getEncounterId());
        item.setFromBedId(fromLocationId);
        item.setToHouseId(request.getHouseLocationId());
        item.setToBedId(request.getBedLocationId());
        command.setItem(item);
        return command;
    }

    /**
     * 互换请求 + 双方活动病房 id → {@link SwapInpatientBedsCommand}。
     *
     * @param request            写请求；调用方保证非 null
     * @param toHouseLocationIdA 就诊 A 活动病房 id
     * @param toHouseLocationIdB 就诊 B 活动病房 id
     * @param operatorId         操作人主键
     * @return 领域互换命令
     */
    @Override
    public SwapInpatientBedsCommand toSwapInpatientBedsCommand(
        ExecuteBedOperationRequest request,
        Long toHouseLocationIdA,
        Long toHouseLocationIdB,
        Long operatorId) {
        SwapInpatientBedsCommand command = new SwapInpatientBedsCommand();
        command.setOperatorId(operatorId);
        SwapInpatientBedsCommand.SwapInpatientBedsItemCommand item =
            new SwapInpatientBedsCommand.SwapInpatientBedsItemCommand();
        item.setEncounterIdA(request.getEncounterId());
        item.setEncounterIdB(request.getEncounterIdB());
        item.setToHouseIdA(toHouseLocationIdA);
        item.setToHouseIdB(toHouseLocationIdB);
        command.setItem(item);
        return command;
    }

    /**
     * 占用床位命令。
     *
     * @param bedLocationId 床位 id
     * @param encounterId   就诊 id
     * @param operatorId    操作人 id
     * @return 占用命令
     */
    @Override
    public OccupyBedCommand toOccupyBedCommand(Long bedLocationId, Long encounterId, Long operatorId) {
        return this.toOccupyBedCommand(bedLocationId, encounterId, operatorId, false);
    }

    @Override
    public OccupyBedCommand toOccupyBedCommand(
        Long bedLocationId,
        Long encounterId,
        Long operatorId,
        boolean occupyFromReservation) {
        OccupyBedCommand command = new OccupyBedCommand();
        command.setOperatorId(operatorId);
        OccupyBedCommand.OccupyBedItemCommand item = new OccupyBedCommand.OccupyBedItemCommand();
        item.setLocationId(bedLocationId);
        item.setEncounterId(encounterId);
        item.setOccupyFromReservation(occupyFromReservation);
        command.setItem(item);
        return command;
    }

    /**
     * 释放床位命令。
     *
     * @param bedLocationId 床位 id
     * @param encounterId   就诊 id
     * @param operatorId    操作人 id
     * @return 释放命令
     */
    @Override
    public ReleaseBedCommand toReleaseBedCommand(Long bedLocationId, Long encounterId, Long operatorId) {
        ReleaseBedCommand command = new ReleaseBedCommand();
        command.setOperatorId(operatorId);
        ReleaseBedCommand.ReleaseBedItemCommand item = new ReleaseBedCommand.ReleaseBedItemCommand();
        item.setLocationId(bedLocationId);
        item.setEncounterId(encounterId);
        command.setItem(item);
        return command;
    }

    /**
     * 互换床：批量释放双方原床（单命令两条 item，A.9.1）。
     *
     * @param operatorId   操作人 id
     * @param bedIdA       就诊 A 原床 id
     * @param encounterIdA 就诊 A id
     * @param bedIdB       就诊 B 原床 id
     * @param encounterIdB 就诊 B id
     * @return 批量释放命令
     */
    @Override
    public ReleaseBedCommand toSwapReleaseBedCommand(
        Long operatorId,
        Long bedIdA,
        Long encounterIdA,
        Long bedIdB,
        Long encounterIdB) {
        ReleaseBedCommand command = new ReleaseBedCommand();
        command.setOperatorId(operatorId);
        List<ReleaseBedCommand.ReleaseBedItemCommand> items = new ArrayList<>();
        ReleaseBedCommand.ReleaseBedItemCommand itemA = new ReleaseBedCommand.ReleaseBedItemCommand();
        itemA.setLocationId(bedIdA);
        itemA.setEncounterId(encounterIdA);
        items.add(itemA);
        ReleaseBedCommand.ReleaseBedItemCommand itemB = new ReleaseBedCommand.ReleaseBedItemCommand();
        itemB.setLocationId(bedIdB);
        itemB.setEncounterId(encounterIdB);
        items.add(itemB);
        command.setItems(items);
        return command;
    }

    /**
     * 互换床：批量占用对调后床位（A→bedB，B→bedA）。
     *
     * @param operatorId    操作人 id
     * @param bedIdForA     就诊 A 迁入床 id
     * @param encounterIdA  就诊 A id
     * @param bedIdForB     就诊 B 迁入床 id
     * @param encounterIdB  就诊 B id
     * @return 批量占用命令
     */
    @Override
    public OccupyBedCommand toSwapOccupyBedCommand(
        Long operatorId,
        Long bedIdForA,
        Long encounterIdA,
        Long bedIdForB,
        Long encounterIdB) {
        OccupyBedCommand command = new OccupyBedCommand();
        command.setOperatorId(operatorId);
        List<OccupyBedCommand.OccupyBedItemCommand> items = new ArrayList<>();
        OccupyBedCommand.OccupyBedItemCommand itemA = new OccupyBedCommand.OccupyBedItemCommand();
        itemA.setLocationId(bedIdForA);
        itemA.setEncounterId(encounterIdA);
        items.add(itemA);
        OccupyBedCommand.OccupyBedItemCommand itemB = new OccupyBedCommand.OccupyBedItemCommand();
        itemB.setLocationId(bedIdForB);
        itemB.setEncounterId(encounterIdB);
        items.add(itemB);
        command.setItems(items);
        return command;
    }

    /**
     * 写请求 → 成功响应。
     *
     * @param request 原请求；调用方保证非 null
     * @return 成功响应
     */
    @Override
    public ExecuteBedOperationResponse toExecuteBedOperationResponse(ExecuteBedOperationRequest request) {
        ExecuteBedOperationResponse response = new ExecuteBedOperationResponse();
        response.setEncounterId(request.getEncounterId());
        response.setOperationType(request.getOperationType());
        response.setSucceeded(Boolean.TRUE);
        return response;
    }

    /**
     * 待执行医嘱行列表 → 响应。
     *
     * @param items 已装配行；调用方保证非 null
     * @return 列表响应
     */
    @Override
    public ListDischargePendingExecutionsResponse toListDischargePendingExecutionsResponse(
        List<DischargePendingExecutionItemDto> items) {
        ListDischargePendingExecutionsResponse response = new ListDischargePendingExecutionsResponse();
        if (items == null) {
            response.setItems(Collections.emptyList());
            return response;
        }
        response.setItems(items);
        return response;
    }

    /**
     * 药品请求 → 待执行医嘱行（IF-IP-NUR-ATD-03）。
     *
     * @param row            药品请求；调用方保证非 null
     * @param medicationName 药品名称；可 null
     * @param doseText       剂量展示；可 null
     * @param blockingReason 阻塞原因码
     * @return 行 DTO；row 为 null 时返回 null
     */
    @Override
    public DischargePendingExecutionItemDto toDischargePendingExecutionItemFromMedication(
        MedicationRequestListQueryModel row,
        String medicationName,
        String doseText,
        String blockingReason) {
        if (row == null) {
            return null;
        }
        DischargePendingExecutionItemDto dto = new DischargePendingExecutionItemDto();
        dto.setRequestId(row.getId());
        dto.setRequestTable(TableNameConstants.MED_MEDICATION_REQUEST);
        dto.setTherapyEnum(row.getTherapyEnum());
        dto.setStatusEnum(row.getStatusEnum());
        dto.setDisplayName(medicationName);
        LocalDateTime openedTime = row.getReqOpenedTime();
        // 出院待执行展示主轴：开立时刻优先，空回落签发追溯列。
        if (openedTime == null) {
            openedTime = row.getReqAuthoredTime();
        }
        dto.setReqOpenedTime(openedTime);
        dto.setRateCode(row.getRateCode());
        dto.setDoseText(doseText);
        dto.setBlockingReason(blockingReason);
        return dto;
    }

    /**
     * 诊疗请求 → 待执行医嘱行（IF-IP-NUR-ATD-03）。
     *
     * @param row             诊疗请求；调用方保证非 null
     * @param activityName    诊疗名称；可 null
     * @param doseText        剂量展示；可 null
     * @param blockingReason  阻塞原因码
     * @return 行 DTO；row 为 null 时返回 null
     */
    @Override
    public DischargePendingExecutionItemDto toDischargePendingExecutionItemFromService(
        ServiceRequestListModel row,
        String activityName,
        String doseText,
        String blockingReason) {
        if (row == null) {
            return null;
        }
        DischargePendingExecutionItemDto dto = new DischargePendingExecutionItemDto();
        dto.setRequestId(row.getId());
        dto.setRequestTable(TableNameConstants.WKF_SERVICE_REQUEST);
        dto.setTherapyEnum(row.getTherapyEnum());
        dto.setStatusEnum(row.getStatusEnum());
        dto.setDisplayName(activityName);
        LocalDateTime openedTime = row.getReqOpenedTime();
        // 出院待执行展示主轴：开立时刻优先，空回落签发追溯列。
        if (openedTime == null) {
            openedTime = row.getReqAuthoredTime();
        }
        dto.setReqOpenedTime(openedTime);
        dto.setRateCode(row.getRateCode());
        dto.setDoseText(doseText);
        dto.setBlockingReason(blockingReason);
        return dto;
    }

    /**
     * 待发退药行列表 → 响应。
     *
     * @param items 已装配行；调用方保证非 null
     * @return 列表响应
     */
    @Override
    public ListDischargePendingMedicationsResponse toListDischargePendingMedicationsResponse(
        List<DischargePendingMedicationItemDto> items) {
        ListDischargePendingMedicationsResponse response = new ListDischargePendingMedicationsResponse();
        if (items == null) {
            response.setItems(Collections.emptyList());
            return response;
        }
        response.setItems(items);
        return response;
    }

    /**
     * 发放读模型 → 待发退药行。
     *
     * @param row                 发放行；调用方保证非 null
     * @param medicationName      药品名称；可 null
     * @param performLocationName 库房名称；可 null
     * @param statusEnumText      状态展示文案；可 null
     * @return 行 DTO；row 为 null 时返回 null
     */
    @Override
    public DischargePendingMedicationItemDto toDischargePendingMedicationItemDto(
        MedicationDispenseListQueryModel row,
        String medicationName,
        String performLocationName,
        String statusEnumText) {
        if (row == null) {
            return null;
        }
        DischargePendingMedicationItemDto dto = new DischargePendingMedicationItemDto();
        dto.setDispenseId(row.getId());
        dto.setMedicationRequestId(row.getMedReqId());
        dto.setMedicationId(row.getMedicationId());
        dto.setMedicationName(medicationName);
        if (row.getStatusEnum() != null) {
            dto.setStatusEnum(row.getStatusEnum().getCode());
        }
        dto.setStatusEnumText(statusEnumText);
        dto.setQuantity(row.getQuantity());
        dto.setUnitCode(row.getUnitCode());
        dto.setPlannedDispenseTime(row.getPlannedDispenseTime());
        dto.setPerformLocationId(row.getLocationId());
        dto.setPerformLocationName(performLocationName);
        return dto;
    }

    /**
     * 办理出院请求 → 领域命令。
     *
     * @param request      写请求；调用方保证非 null
     * @param operatorId   操作人主键
     * @param dischargedAt 出院时间
     * @return 护士办理出院命令
     */
    @Override
    public ExecuteInpatientNurseDischargeCommand toExecuteInpatientNurseDischargeCommand(
        ExecuteInpatientDischargeRequest request,
        Long operatorId,
        LocalDateTime dischargedAt) {
        ExecuteInpatientNurseDischargeCommand command = new ExecuteInpatientNurseDischargeCommand();
        command.setOperatorId(operatorId);
        ExecuteInpatientNurseDischargeCommand.ExecuteInpatientNurseDischargeItemCommand item =
            new ExecuteInpatientNurseDischargeCommand.ExecuteInpatientNurseDischargeItemCommand();
        item.setEncounterId(request.getEncounterId());
        item.setDischargedAt(dischargedAt);
        command.setItem(item);
        return command;
    }

    /**
     * 办理出院成功响应。
     *
     * @param encounterId 就诊主键
     * @return 成功响应
     */
    @Override
    public ExecuteInpatientDischargeResponse toExecuteInpatientDischargeResponse(Long encounterId) {
        ExecuteInpatientDischargeResponse response = new ExecuteInpatientDischargeResponse();
        response.setEncounterId(encounterId);
        response.setSucceeded(Boolean.TRUE);
        return response;
    }

    /**
     * 清床请求 + 活动床 → 领域清床命令。
     *
     * @param request    写请求；调用方保证非 null
     * @param operatorId 操作人主键
     * @param bedId      活动床 locationId
     * @param clearedAt  清床时间
     * @return 清床命令
     */
    @Override
    public ClearDischargedBedCommand toClearDischargedBedCommand(
        ClearDischargedBedRequest request,
        Long operatorId,
        Long bedId,
        LocalDateTime clearedAt) {
        ClearDischargedBedCommand command = new ClearDischargedBedCommand();
        command.setOperatorId(operatorId);
        ClearDischargedBedCommand.ClearDischargedBedItemCommand item =
            new ClearDischargedBedCommand.ClearDischargedBedItemCommand();
        item.setEncounterId(request.getEncounterId());
        item.setBedId(bedId);
        item.setClearedAt(clearedAt);
        command.setItem(item);
        return command;
    }

    /**
     * 清床成功响应。
     *
     * @param encounterId 就诊主键
     * @return 成功响应
     */
    @Override
    public ClearDischargedBedResponse toClearDischargedBedResponse(Long encounterId) {
        ClearDischargedBedResponse response = new ClearDischargedBedResponse();
        response.setEncounterId(encounterId);
        response.setSucceeded(Boolean.TRUE);
        return response;
    }
}
