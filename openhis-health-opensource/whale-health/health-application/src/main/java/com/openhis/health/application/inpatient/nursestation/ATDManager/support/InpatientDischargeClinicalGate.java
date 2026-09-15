/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ATDManager.support;

import com.openhis.health.application.inpatient.nursestation.orderexecute.support.NurseStationOrderExecuteListReadAssembler;
import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseListQueryModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 住院出院临床门禁（AS-IP-NUR-002 ATD-03/04/05 与 AS-IP-FIN-003 出院全结 Pre 共用）。
 *
 * <p>口径：长期未停嘱、临时未完结、待发/待退药须清零，方可办理出院或出院全结结算。
 * 已核发缺追溯码改为前端软提示（{@code listMissingDispenseTraceTips}），本门禁不再硬拦。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientDischargeClinicalGate {

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ProcedureQueryService procedureQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final NurseStationOrderExecuteListReadAssembler nurseStationOrderExecuteListReadAssembler;

    /**
     * 断言就诊已通过出院临床门禁（办理出院 / 出院全结结算写路径）。
     *
     * @param encounterId 就诊主键
     * @throws BusinessException EX-IP-ATD-301/302/303/307
     */
    public void assertDischargeClinicalGatePassed(Long encounterId) {
        if (encounterId == null) {
            return;
        }
        // Pre：与 ATD-03 待办列表同数据源——存在阻塞项则拒绝写路径。
        DischargePendingExecutionScan scan = this.loadDischargePendingExecutionScan(encounterId);
        String blockingReason = this.findFirstDischargeExecutionBlockingReason(scan);
        if (blockingReason != null) {
            this.throwDischargeGateExceptionForBlockingReason(blockingReason);
        }
        if (!this.listDischargePendingMedicationRows(encounterId).isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_303,
                "患者有待取的药品，请先取药");
        }
        // 追溯码缺码：不再硬拦；由前端 listMissingDispenseTraceTips 提示后仍可清床
    }

    /**
     * ATD-03/05 共用：加载出院前医嘱扫描上下文。
     *
     * @param encounterId 就诊主键
     * @return 药品/诊疗请求与 Procedure 分组
     */
    public DischargePendingExecutionScan loadDischargePendingExecutionScan(Long encounterId) {
        List<MedicationRequestListQueryModel> medicationRequests =
            medicationRequestQueryService.listMedicationRequestsByEncounter(encounterId);
        List<ServiceRequestListModel> serviceRequests =
            serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        List<Procedure> procedures =
            procedureQueryService.listByEncounterIds(Collections.singleton(encounterId));
        Map<String, List<Procedure>> proceduresByRequestKey =
            this.buildProceduresByRequestKey(procedures);
        LocalDateTime executionDeadline = LocalDateTime.now();
        return new DischargePendingExecutionScan(
            medicationRequests,
            serviceRequests,
            proceduresByRequestKey,
            executionDeadline);
    }

    /**
     * ATD-04/05 共用：出院前待发/待退药行（BR-IP-ATD-015）。
     *
     * @param encounterId 就诊主键
     * @return 阻塞出院的发放行；无则空列表
     */
    public List<MedicationDispenseListQueryModel> listDischargePendingMedicationRows(Long encounterId) {
        List<MedicationDispenseListQueryModel> dispenseRows =
            medicationDispenseQueryService.listMedicationDispensesByEncounter(encounterId);
        List<MedicationDispenseListQueryModel> pendingRows = new ArrayList<>();
        for (MedicationDispenseListQueryModel row : dispenseRows) {
            if (row == null || row.getStatusEnum() == null) {
                continue;
            }
            if (!this.isPendingDischargeDispenseStatus(row.getStatusEnum().getCode())) {
                continue;
            }
            pendingRows.add(row);
        }
        return pendingRows;
    }

    /**
     * ATD-03 列表候选：已校对医生开立药品，排除退药关联。
     *
     * @param row 药品请求
     * @return true 表示可参与待执行筛选
     */
    public boolean isDischargePendingListCandidateMedication(MedicationRequestListQueryModel row) {
        if (row == null) {
            return false;
        }
        if (!this.isDischargePendingRequestStatusEligible(row.getStatusEnum())) {
            return false;
        }
        if (!GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(row.getGenerateSourceEnum())) {
            return false;
        }
        if (this.isRefundDerivativeMedication(row)) {
            return false;
        }
        return true;
    }

    /**
     * ATD-03 列表候选：已校对医生开立诊疗，排除转科/出院/护理及子项、退药关联。
     *
     * @param row 诊疗请求
     * @return true 表示可参与待执行筛选
     */
    public boolean isDischargePendingListCandidateService(ServiceRequestListModel row) {
        if (row == null) {
            return false;
        }
        if (!this.isDischargePendingRequestStatusEligible(row.getStatusEnum())) {
            return false;
        }
        if (!GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(row.getGenerateSourceEnum())) {
            return false;
        }
        if (this.isExcludedDischargeServiceCategory(row.getCategoryEnum())) {
            return false;
        }
        if (row.getParentId() != null) {
            return false;
        }
        if (this.isRefundDerivativeService(row)) {
            return false;
        }
        return true;
    }

    /**
     * 待执行列表阻塞原因：长期未停嘱、长期已停嘱仍有待执行时点、或临时未完结。
     *
     * @param therapyEnum            长临 code
     * @param statusEnum             状态 code
     * @param requestTable           来源表
     * @param requestId              请求主键
     * @param proceduresByRequestKey 按 requestTable+requestId 分组的执行记录
     * @param medRow                 药品请求；非药嘱传 null
     * @param svcRow                 诊疗请求；非诊嘱传 null
     * @param executionDeadline      槽位截止时刻
     * @return 阻塞原因码；无须展示时 null
     */
    public String resolveDischargeListBlockingReason(
        String therapyEnum,
        String statusEnum,
        String requestTable,
        Long requestId,
        Map<String, List<Procedure>> proceduresByRequestKey,
        MedicationRequestListQueryModel medRow,
        ServiceRequestListModel svcRow,
        LocalDateTime executionDeadline) {
        if (this.isLongTermNotStopped(therapyEnum, statusEnum)) {
            return InpatientNurseStationConstants.DISCHARGE_BLOCKING_LONG_NOT_STOPPED;
        }
        if (this.isLongTermStoppedWithPendingExecutionSlots(
            medRow,
            svcRow,
            proceduresByRequestKey,
            executionDeadline)) {
            return InpatientNurseStationConstants.DISCHARGE_BLOCKING_LONG_PENDING_SLOTS;
        }
        if (this.isTemporaryNotCompleted(therapyEnum, requestTable, requestId, proceduresByRequestKey)) {
            return InpatientNurseStationConstants.DISCHARGE_BLOCKING_TEMP_NOT_COMPLETED;
        }
        return null;
    }

    /**
     * ATD-05：取 ATD-03 口径下第一条医嘱阻塞原因；无阻塞时 null。
     *
     * @param scan 医嘱扫描上下文
     * @return 阻塞原因码；无则 null
     */
    public String findFirstDischargeExecutionBlockingReason(DischargePendingExecutionScan scan) {
        if (scan == null) {
            return null;
        }
        for (MedicationRequestListQueryModel row : scan.getMedicationRequests()) {
            if (row == null || !this.isDischargePendingListCandidateMedication(row)) {
                continue;
            }
            String blockingReason = this.resolveDischargeListBlockingReason(
                row.getTherapyEnum(),
                row.getStatusEnum(),
                TableNameConstants.MED_MEDICATION_REQUEST,
                row.getId(),
                scan.getProceduresByRequestKey(),
                row,
                null,
                scan.getExecutionDeadline());
            if (blockingReason != null) {
                return blockingReason;
            }
        }
        for (ServiceRequestListModel row : scan.getServiceRequests()) {
            if (row == null || !this.isDischargePendingListCandidateService(row)) {
                continue;
            }
            String blockingReason = this.resolveDischargeListBlockingReason(
                row.getTherapyEnum(),
                row.getStatusEnum(),
                TableNameConstants.WKF_SERVICE_REQUEST,
                row.getId(),
                scan.getProceduresByRequestKey(),
                null,
                row,
                scan.getExecutionDeadline());
            if (blockingReason != null) {
                return blockingReason;
            }
        }
        return null;
    }

    /**
     * 阻塞原因码 → 业务异常（与 ATD-03 blockingReason 一一对应）。
     *
     * @param blockingReason {@link InpatientNurseStationConstants#DISCHARGE_BLOCKING_*} 码
     * @throws BusinessException EX-IP-ATD-301/302/307
     */
    public void throwDischargeGateExceptionForBlockingReason(String blockingReason) {
        if (InpatientNurseStationConstants.DISCHARGE_BLOCKING_LONG_NOT_STOPPED.equals(blockingReason)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_301,
                "长期医嘱需要先停嘱");
        }
        if (InpatientNurseStationConstants.DISCHARGE_BLOCKING_LONG_PENDING_SLOTS.equals(blockingReason)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_307,
                "长期医嘱仍有待执行时点，请先执行或不执行登记");
        }
        if (InpatientNurseStationConstants.DISCHARGE_BLOCKING_TEMP_NOT_COMPLETED.equals(blockingReason)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_302,
                "存在未执行或未完成的临时医嘱，请先执行");
        }
        throw new BusinessException(
            InpatientNurseStationConstants.EX_IP_ATD_306,
            "出院失败，请刷新后重试");
    }

    /**
     * ATD-03/05 出院前医嘱扫描上下文（唯一数据源）。
     */
    public static final class DischargePendingExecutionScan {

        private final List<MedicationRequestListQueryModel> medicationRequests;
        private final List<ServiceRequestListModel> serviceRequests;
        private final Map<String, List<Procedure>> proceduresByRequestKey;
        private final LocalDateTime executionDeadline;

        /**
         * @param medicationRequests     就诊下全部药品请求读模型
         * @param serviceRequests        就诊下全部诊疗请求读模型
         * @param proceduresByRequestKey 按 request 键分组的执行记录
         * @param executionDeadline      长期槽位截止时刻
         */
        public DischargePendingExecutionScan(
            List<MedicationRequestListQueryModel> medicationRequests,
            List<ServiceRequestListModel> serviceRequests,
            Map<String, List<Procedure>> proceduresByRequestKey,
            LocalDateTime executionDeadline) {
            this.medicationRequests = medicationRequests;
            this.serviceRequests = serviceRequests;
            this.proceduresByRequestKey = proceduresByRequestKey;
            this.executionDeadline = executionDeadline;
        }

        /** @return 药品请求读模型列表 */
        public List<MedicationRequestListQueryModel> getMedicationRequests() {
            return medicationRequests;
        }

        /** @return 诊疗请求读模型列表 */
        public List<ServiceRequestListModel> getServiceRequests() {
            return serviceRequests;
        }

        /** @return 按 request 键分组的 Procedure */
        public Map<String, List<Procedure>> getProceduresByRequestKey() {
            return proceduresByRequestKey;
        }

        /** @return 长期槽位截止时刻 */
        public LocalDateTime getExecutionDeadline() {
            return executionDeadline;
        }
    }

    /**
     * ATD-03/05 候选：已校对或已停嘱（STOPPED 长期仍须判定待执行时点）。
     *
     * @param statusEnum 请求状态 code
     * @return true 表示可参与待执行筛选
     */
    private boolean isDischargePendingRequestStatusEligible(String statusEnum) {
        if (RequestStatus.VERIFIED.getCode().equals(statusEnum)) {
            return true;
        }
        if (RequestStatus.STOPPED.getCode().equals(statusEnum)) {
            return true;
        }
        return false;
    }

    /**
     * 长期已停嘱且停嘱时刻前仍有待执行槽位（与 Q-IP-EXE-001 同源，含一盒一点/拆零下限切批）。
     *
     * @param medRow                 药品请求；非药嘱传 null
     * @param svcRow                 诊疗请求；非诊嘱传 null
     * @param proceduresByRequestKey 按 request 分组的 Procedure
     * @param executionDeadline      截止时刻
     * @return true 表示阻塞出院
     */
    private boolean isLongTermStoppedWithPendingExecutionSlots(
        MedicationRequestListQueryModel medRow,
        ServiceRequestListModel svcRow,
        Map<String, List<Procedure>> proceduresByRequestKey,
        LocalDateTime executionDeadline) {
        String therapyEnum = null;
        String statusEnum = null;
        String requestTable = null;
        Long requestId = null;
        if (medRow != null) {
            therapyEnum = medRow.getTherapyEnum();
            statusEnum = medRow.getStatusEnum();
            requestTable = TableNameConstants.MED_MEDICATION_REQUEST;
            requestId = medRow.getId();
        } else if (svcRow != null) {
            therapyEnum = svcRow.getTherapyEnum();
            statusEnum = svcRow.getStatusEnum();
            requestTable = TableNameConstants.WKF_SERVICE_REQUEST;
            requestId = svcRow.getId();
        }
        if (!TherapyType.LONG_TERM.getCode().equals(therapyEnum)) {
            return false;
        }
        if (!RequestStatus.STOPPED.getCode().equals(statusEnum)) {
            return false;
        }
        if (requestId == null || StringUtils.isBlank(requestTable)) {
            return false;
        }
        String key = this.buildRequestKey(requestTable, requestId);
        List<Procedure> procedures = null;
        if (proceduresByRequestKey != null) {
            procedures = proceduresByRequestKey.get(key);
        }
        List<LocalDateTime> pendingPlanTimes =
            nurseStationOrderExecuteListReadAssembler.listLongTermStoppedPendingPlanTimes(
                medRow,
                svcRow,
                procedures,
                executionDeadline);
        if (pendingPlanTimes == null || pendingPlanTimes.isEmpty()) {
            // 与 ATD-03 pendingPlanTimes 空列表同口径：不拦出院
            return false;
        }
        return true;
    }

    /**
     * ATD-03：长期已停嘱 pending 计划点（与门禁 {@link #isLongTermStoppedWithPendingExecutionSlots} 同源）。
     *
     * @param medRow                 药品请求；非药嘱传 null
     * @param svcRow                 诊疗请求；非诊嘱传 null
     * @param proceduresByRequestKey 按 request 分组的 Procedure
     * @param executionDeadline      截止时刻
     * @return pending planTime；非长期已停嘱或无 pending 时为空列表
     */
    public List<LocalDateTime> listLongTermStoppedPendingPlanTimes(
        MedicationRequestListQueryModel medRow,
        ServiceRequestListModel svcRow,
        Map<String, List<Procedure>> proceduresByRequestKey,
        LocalDateTime executionDeadline) {
        String requestTable = null;
        Long requestId = null;
        if (medRow != null) {
            requestTable = TableNameConstants.MED_MEDICATION_REQUEST;
            requestId = medRow.getId();
        } else if (svcRow != null) {
            requestTable = TableNameConstants.WKF_SERVICE_REQUEST;
            requestId = svcRow.getId();
        }
        if (requestId == null || StringUtils.isBlank(requestTable)) {
            // 无请求锚点：无法对 Procedure 分组键
            return Collections.emptyList();
        }
        String key = this.buildRequestKey(requestTable, requestId);
        List<Procedure> procedures = null;
        if (proceduresByRequestKey != null) {
            procedures = proceduresByRequestKey.get(key);
        }
        List<LocalDateTime> pendingPlanTimes =
            nurseStationOrderExecuteListReadAssembler.listLongTermStoppedPendingPlanTimes(
                medRow,
                svcRow,
                procedures,
                executionDeadline);
        if (pendingPlanTimes == null) {
            return Collections.emptyList();
        }
        return pendingPlanTimes;
    }

    /**
     * 长期医嘱且尚未停嘱。
     *
     * @param therapyEnum 长临 code
     * @param statusEnum  状态 code
     * @return true 表示长期未停
     */
    private boolean isLongTermNotStopped(String therapyEnum, String statusEnum) {
        if (!TherapyType.LONG_TERM.getCode().equals(therapyEnum)) {
            return false;
        }
        if (RequestStatus.STOPPED.getCode().equals(statusEnum)) {
            return false;
        }
        return true;
    }

    /**
     * 临时医嘱净态未完结（与 AS-IP-NUR-004 BR-IP-EXE-006/008 同源）。
     *
     * @param therapyEnum            长临 code
     * @param requestTable           来源表
     * @param requestId              请求主键
     * @param proceduresByRequestKey 按 requestTable+requestId 分组的执行记录
     * @return true 表示临时未完结
     */
    private boolean isTemporaryNotCompleted(
        String therapyEnum,
        String requestTable,
        Long requestId,
        Map<String, List<Procedure>> proceduresByRequestKey) {
        if (!TherapyType.TEMPORARY.getCode().equals(therapyEnum)) {
            return false;
        }
        if (requestId == null || StringUtils.isBlank(requestTable)) {
            return true;
        }
        String key = this.buildRequestKey(requestTable, requestId);
        List<Procedure> procedures = proceduresByRequestKey.get(key);
        if (procedures == null || procedures.isEmpty()) {
            // 临时嘱尚无执行记录 → 待执行，阻塞出院/结算
            return true;
        }
        return nurseStationOrderExecuteListReadAssembler.isTemporaryOrderNetPendingForDischarge(procedures);
    }

    /**
     * 是否为出院门禁关注的待发/待退药状态。
     *
     * @param statusCode 发放状态 code
     * @return true 表示阻塞出院
     */
    private boolean isPendingDischargeDispenseStatus(String statusCode) {
        if (DispenseStatus.PREPARATION.getCode().equals(statusCode)) {
            return true;
        }
        if (DispenseStatus.SUMMARIZED.getCode().equals(statusCode)) {
            return true;
        }
        if (DispenseStatus.PENDING_REFUND.getCode().equals(statusCode)) {
            return true;
        }
        return false;
    }

    /**
     * 排除转科/出院/护理类诊疗类目。
     *
     * @param categoryEnum 诊疗类目 code
     * @return true 表示应排除
     */
    private boolean isExcludedDischargeServiceCategory(String categoryEnum) {
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_TRANSFER.equals(categoryEnum)) {
            return true;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(categoryEnum)) {
            return true;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_NURSING.equals(categoryEnum)) {
            return true;
        }
        return false;
    }

    /**
     * 药品退药负向 Request 防御识别（住院退药不造负向药嘱；认 {@code refund_medicine_id}；禁止用 based_on）。
     *
     * @param row 药品请求
     * @return true 表示负向药嘱行（住院正常数据通常不命中）
     */
    private boolean isRefundDerivativeMedication(MedicationRequestListQueryModel row) {
        return row.getRefundMedicineId() != null;
    }

    /**
     * 诊疗退诊疗负向 Request 防御识别（住院取消执行回退账单，不造负向诊嘱；认 {@code refund_service_id}/负数量；禁止用 based_on）。
     *
     * @param row 诊疗请求
     * @return true 表示负向诊嘱行（住院正常数据通常不命中）
     */
    private boolean isRefundDerivativeService(ServiceRequestListModel row) {
        if (row.getRefundServiceId() != null) {
            return true;
        }
        if (row.getQuantity() != null && row.getQuantity().signum() < 0) {
            return true;
        }
        return false;
    }

    /**
     * 按 requestTable+requestId 分组全部 Procedure（出院门禁净态判定用）。
     *
     * @param procedures 执行记录列表
     * @return 请求键 → 该请求下全部 Procedure
     */
    private Map<String, List<Procedure>> buildProceduresByRequestKey(List<Procedure> procedures) {
        Map<String, List<Procedure>> grouped = new HashMap<>();
        if (procedures == null) {
            return grouped;
        }
        for (Procedure procedure : procedures) {
            if (procedure == null || procedure.getRequestId() == null
                || StringUtils.isBlank(procedure.getRequestTable())) {
                continue;
            }
            String key = this.buildRequestKey(procedure.getRequestTable(), procedure.getRequestId());
            List<Procedure> bucket = grouped.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(key, bucket);
            }
            bucket.add(procedure);
        }
        return grouped;
    }

    /**
     * 请求自然键（requestTable + requestId）。
     *
     * @param requestTable 来源表
     * @param requestId    主键
     * @return 复合键
     */
    private String buildRequestKey(String requestTable, Long requestId) {
        return requestTable.trim() + "\u0001" + requestId;
    }
}
