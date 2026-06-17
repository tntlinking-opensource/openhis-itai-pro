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

package com.openhis.health.application.inpatient.nursestation.ATDManager.mapper;

import com.openhis.health.module.common.assembler.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.BedBoardItemDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.BedBoardLocationBlockDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.BedBoardOccupantDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.DischargePendingExecutionItemDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.DischargePendingMedicationItemDto;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ClearDischargedBedRequest;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ExecuteBedOperationRequest;
import com.openhis.health.application.inpatient.nursestation.ATDManager.request.ExecuteInpatientDischargeRequest;
import com.openhis.health.application.inpatient.nursestation.ATDManager.response.ClearDischargedBedResponse;
import com.openhis.health.application.inpatient.nursestation.ATDManager.response.ExecuteBedOperationResponse;
import com.openhis.health.application.inpatient.nursestation.ATDManager.response.ExecuteInpatientDischargeResponse;
import com.openhis.health.application.inpatient.nursestation.ATDManager.response.ListBedBoardResponse;
import com.openhis.health.application.inpatient.nursestation.ATDManager.response.ListDischargePendingExecutionsResponse;
import com.openhis.health.application.inpatient.nursestation.ATDManager.response.ListDischargePendingMedicationsResponse;
import com.openhis.health.domain.administration.encounter.command.ClearDischargedBedCommand;
import com.openhis.health.domain.administration.encounter.command.ExecuteInpatientNurseDischargeCommand;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;

import java.time.LocalDateTime;
import com.openhis.health.domain.administration.encounter.command.AssignInpatientBedCommand;
import com.openhis.health.domain.administration.encounter.command.ConfirmBedTransferCommand;
import com.openhis.health.domain.administration.encounter.command.SwapInpatientBedsCommand;
import com.openhis.health.domain.administration.location.command.OccupyBedCommand;
import com.openhis.health.domain.administration.location.command.ReleaseBedCommand;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;

import java.math.BigDecimal;
import java.util.List;

/**
 * 住院护士站入出转（ATD）Request/Command/Response 映射；禁止承载业务流程判断。
 */
public interface NurseStationATDManagerMapper {

    /**
     * 床位列表行 → 列表响应。
     *
     * @param items 已装配列表项；调用方保证非 null
     * @return 列表响应
     */
    ListBedBoardResponse toListBedBoardResponse(List<BedBoardItemDto> items);

    /**
     * 床位主数据 + 病房展示名 → 位置块 DTO（BR-IP-ATD-001）。
     *
     * @param bed       床位 Location；调用方保证非 null
     * @param houseName 所属病房名称；可 null
     * @return 位置块；bed 为 null 时返回 null
     */
    BedBoardLocationBlockDto toBedBoardLocationBlock(Location bed, String houseName);

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
    BedBoardOccupantDto toBedBoardOccupantDto(
        InpatientEncounterContextQueryModel row,
        InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay overlay,
        BigDecimal accountBalance,
        boolean lowBalanceWarning,
        String priorityEnum);

    /**
     * 分床请求 → {@link AssignInpatientBedCommand}。
     *
     * @param request    写请求；调用方保证非 null
     * @param operatorId 操作人主键
     * @return 领域分床命令
     */
    AssignInpatientBedCommand toAssignInpatientBedCommand(ExecuteBedOperationRequest request, Long operatorId);

    /**
     * 换床请求 + 原床 id → {@link ConfirmBedTransferCommand}。
     *
     * @param request        写请求；调用方保证非 null
     * @param fromLocationId 当前活动床 locationId
     * @param operatorId     操作人主键
     * @return 领域转床命令
     */
    ConfirmBedTransferCommand toConfirmBedTransferCommand(
        ExecuteBedOperationRequest request,
        Long fromLocationId,
        Long operatorId);

    /**
     * 互换请求 + 双方活动病房 id → {@link SwapInpatientBedsCommand}。
     *
     * @param request            写请求；调用方保证非 null
     * @param toHouseLocationIdA 就诊 A 活动病房 id
     * @param toHouseLocationIdB 就诊 B 活动病房 id
     * @param operatorId         操作人主键
     * @return 领域互换命令
     */
    SwapInpatientBedsCommand toSwapInpatientBedsCommand(
        ExecuteBedOperationRequest request,
        Long toHouseLocationIdA,
        Long toHouseLocationIdB,
        Long operatorId);

    /**
     * 占用床位命令。
     *
     * @param bedLocationId 床位 id
     * @param encounterId   就诊 id
     * @param operatorId    操作人 id
     * @return 占用命令
     */
    OccupyBedCommand toOccupyBedCommand(Long bedLocationId, Long encounterId, Long operatorId);

    /**
     * 释放床位命令（单条）。
     *
     * @param bedLocationId 床位 id
     * @param encounterId   就诊 id
     * @param operatorId    操作人 id
     * @return 释放命令
     */
    ReleaseBedCommand toReleaseBedCommand(Long bedLocationId, Long encounterId, Long operatorId);

    /**
     * 互换床：批量释放双方原床（单命令两条 item，A.9.1）。
     *
     * @param operatorId  操作人 id
     * @param bedIdA      就诊 A 原床 id
     * @param encounterIdA 就诊 A id
     * @param bedIdB      就诊 B 原床 id
     * @param encounterIdB 就诊 B id
     * @return 批量释放命令
     */
    ReleaseBedCommand toSwapReleaseBedCommand(
        Long operatorId,
        Long bedIdA,
        Long encounterIdA,
        Long bedIdB,
        Long encounterIdB);

    /**
     * 互换床：批量占用对调后床位（A→bedB，B→bedA）。
     *
     * @param operatorId   操作人 id
     * @param bedIdForA    就诊 A 迁入床 id（原 B 床）
     * @param encounterIdA 就诊 A id
     * @param bedIdForB    就诊 B 迁入床 id（原 A 床）
     * @param encounterIdB 就诊 B id
     * @return 批量占用命令
     */
    OccupyBedCommand toSwapOccupyBedCommand(
        Long operatorId,
        Long bedIdForA,
        Long encounterIdA,
        Long bedIdForB,
        Long encounterIdB);

    /**
     * 写请求 → 成功响应。
     *
     * @param request 原请求；调用方保证非 null
     * @return 成功响应
     */
    ExecuteBedOperationResponse toExecuteBedOperationResponse(ExecuteBedOperationRequest request);

    /**
     * 待执行医嘱行列表 → 响应。
     *
     * @param items 已装配行；调用方保证非 null
     * @return 列表响应
     */
    ListDischargePendingExecutionsResponse toListDischargePendingExecutionsResponse(
        List<DischargePendingExecutionItemDto> items);

    /**
     * 药品请求 → 待执行医嘱行（IF-IP-NUR-ATD-03）。
     *
     * @param row            药品请求；调用方保证非 null
     * @param medicationName 药品名称；可 null
     * @param doseText       剂量展示；可 null
     * @param blockingReason 阻塞原因码
     * @return 行 DTO；row 为 null 时返回 null
     */
    DischargePendingExecutionItemDto toDischargePendingExecutionItemFromMedication(
        MedicationRequestListQueryModel row,
        String medicationName,
        String doseText,
        String blockingReason);

    /**
     * 诊疗请求 → 待执行医嘱行（IF-IP-NUR-ATD-03）。
     *
     * @param row             诊疗请求；调用方保证非 null
     * @param activityName    诊疗名称；可 null
     * @param doseText        剂量展示；可 null
     * @param blockingReason  阻塞原因码
     * @return 行 DTO；row 为 null 时返回 null
     */
    DischargePendingExecutionItemDto toDischargePendingExecutionItemFromService(
        ServiceRequestListModel row,
        String activityName,
        String doseText,
        String blockingReason);

    /**
     * 待发退药行列表 → 响应。
     *
     * @param items 已装配行；调用方保证非 null
     * @return 列表响应
     */
    ListDischargePendingMedicationsResponse toListDischargePendingMedicationsResponse(
        List<DischargePendingMedicationItemDto> items);

    /**
     * 发放读模型 → 待发退药行。
     *
     * @param row                 发放行；调用方保证非 null
     * @param medicationName      药品名称；可 null
     * @param performLocationName 库房名称；可 null
     * @param statusEnumText      状态展示文案；可 null
     * @return 行 DTO；row 为 null 时返回 null
     */
    DischargePendingMedicationItemDto toDischargePendingMedicationItemDto(
        MedicationDispenseListQueryModel row,
        String medicationName,
        String performLocationName,
        String statusEnumText);

    /**
     * 办理出院请求 → 领域命令。
     *
     * @param request      写请求；调用方保证非 null
     * @param operatorId   操作人主键
     * @param dischargedAt 出院时间
     * @return 护士办理出院命令
     */
    ExecuteInpatientNurseDischargeCommand toExecuteInpatientNurseDischargeCommand(
        ExecuteInpatientDischargeRequest request,
        Long operatorId,
        LocalDateTime dischargedAt);

    /**
     * 办理出院成功响应。
     *
     * @param encounterId 就诊主键
     * @return 成功响应
     */
    ExecuteInpatientDischargeResponse toExecuteInpatientDischargeResponse(Long encounterId);

    /**
     * 清床请求 + 活动床 → 领域清床命令。
     *
     * @param request    写请求；调用方保证非 null
     * @param operatorId 操作人主键
     * @param bedId      活动床 locationId
     * @param clearedAt  清床时间
     * @return 清床命令
     */
    ClearDischargedBedCommand toClearDischargedBedCommand(
        ClearDischargedBedRequest request,
        Long operatorId,
        Long bedId,
        LocalDateTime clearedAt);

    /**
     * 清床成功响应。
     *
     * @param encounterId 就诊主键
     * @return 成功响应
     */
    ClearDischargedBedResponse toClearDischargedBedResponse(Long encounterId);
}
