/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderaudit.support;

import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderAuditWriteItemDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteWriteSlotDto;
import com.openhis.health.application.inpatient.nursestation.orderexecute.support.NurseStationOrderExecuteListReadAssembler;
import com.openhis.health.application.shared.advice.InpatientStopVerifyAutoSkipPendingSettingResolver;
import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 停嘱校对成功后按开关自动登记剩余 pending 为不执行（IF-IP-AUD-02 可选编排）。
 *
 * <p>pending 时点与出院门禁 / 执行列表同源（{@code NurseStationOrderExecuteListReadAssembler}）；
 * 写路径复用 IF-IP-EXE-04，不放宽 {@code BR-IP-ATD-011①′}。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientStopVerifyAutoSkipPendingSupport {

    /** 停嘱校对后自动 skip 开关。 */
    private final InpatientStopVerifyAutoSkipPendingSettingResolver autoSkipPendingSettingResolver;
    /** 执行列表槽位判定（与 ATD-03/05 门禁同源）。 */
    private final NurseStationOrderExecuteListReadAssembler nurseStationOrderExecuteListReadAssembler;
    /** 药品请求读模型。 */
    private final MedicationRequestQueryService medicationRequestQueryService;
    /** 诊疗请求读模型。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    /** Procedure 批量加载。 */
    private final ProcedureQueryService procedureQueryService;

    /**
     * 收集刚完成停嘱校对的长期医嘱上、停嘱前仍 pending 的计划点。
     *
     * <p>开关关闭或无停嘱校对项时返回空列表；调用方须再走 IF-IP-EXE-04 写不执行，
     * 不得在本 Support 内组装 Request/Response。</p>
     *
     * @param stopVerifiedItems 本批已成功停嘱校对的写锚点；可空
     * @return 与出院门禁同源的待 skip 槽位；无则空列表
     */
    public List<NurseStationOrderExecuteWriteSlotDto> collectRemainingPendingSlotsAfterStopVerify(
        List<NurseStationOrderAuditWriteItemDto> stopVerifiedItems) {
        // 开关关闭：不收集槽位，出院门禁仍按 BR-IP-ATD-011①′拦截未 skip 的 pending
        if (!autoSkipPendingSettingResolver.isEnabled()) {
            return Collections.emptyList();
        }
        // 本批无停嘱校对成功项：无需 skip
        if (stopVerifiedItems == null || stopVerifiedItems.isEmpty()) {
            return Collections.emptyList();
        }
        LocalDateTime executionDeadline = LocalDateTime.now();
        Map<Long, MedicationRequestListQueryModel> medById = this.loadMedicationModels(stopVerifiedItems);
        Map<Long, ServiceRequestListModel> svcById = this.loadServiceModels(stopVerifiedItems);
        Set<Long> encounterIds = this.collectEncounterIds(stopVerifiedItems, medById, svcById);
        Map<String, List<Procedure>> proceduresByRequestKey = this.loadProceduresByRequestKey(encounterIds);
        List<NurseStationOrderExecuteWriteSlotDto> skipItems = new ArrayList<>();
        // 逐条解析与门禁同源的 pending 点，交由 AppService 调 IF-IP-EXE-04
        for (NurseStationOrderAuditWriteItemDto item : stopVerifiedItems) {
            this.appendSkipSlotsForStopVerifiedItem(
                item,
                executionDeadline,
                medById,
                svcById,
                proceduresByRequestKey,
                skipItems);
        }
        return skipItems;
    }

    /**
     * 将一条停嘱校对成功的医嘱上尚未终态的 pending 计划点加入 skip 列表。
     *
     * @param item                   停嘱校对锚点
     * @param executionDeadline      与出院门禁相同的截止时刻
     * @param medById                已批量装载的药品读模型
     * @param svcById                已批量装载的诊疗读模型
     * @param proceduresByRequestKey 按 request 键分组的 Procedure
     * @param skipItems              待提交的不执行槽位（就地追加）
     */
    private void appendSkipSlotsForStopVerifiedItem(
        NurseStationOrderAuditWriteItemDto item,
        LocalDateTime executionDeadline,
        Map<Long, MedicationRequestListQueryModel> medById,
        Map<Long, ServiceRequestListModel> svcById,
        Map<String, List<Procedure>> proceduresByRequestKey,
        List<NurseStationOrderExecuteWriteSlotDto> skipItems) {
        // 锚点不完整则无法写 Procedure occurrence
        if (item == null || item.getRequestId() == null || StringUtils.isEmpty(item.getOrderType())) {
            return;
        }
        MedicationRequestListQueryModel medRow = null;
        ServiceRequestListModel svcRow = null;
        String requestTable;
        // 药嘱：用预加载读模型，requestTable 与门禁 / 执行列表一致
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
            medRow = medById.get(item.getRequestId());
            requestTable = TableNameConstants.MED_MEDICATION_REQUEST;
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(item.getOrderType())) {
            // 诊嘱：同上，禁止另算日频次点
            svcRow = svcById.get(item.getRequestId());
            requestTable = TableNameConstants.WKF_SERVICE_REQUEST;
        } else {
            // 本期仅药/诊两类可 skip
            return;
        }
        String key = this.buildRequestKey(requestTable, item.getRequestId());
        List<Procedure> procedures = proceduresByRequestKey.get(key);
        List<LocalDateTime> pendingPlanTimes =
            nurseStationOrderExecuteListReadAssembler.listLongTermStoppedPendingPlanTimes(
                medRow,
                svcRow,
                procedures,
                executionDeadline);
        // 无 pending：停嘱硬顶未满盒已空解析，或时点均已终态
        if (pendingPlanTimes == null || pendingPlanTimes.isEmpty()) {
            return;
        }
        for (LocalDateTime planTime : pendingPlanTimes) {
            // 空时刻不写 Procedure occurrence
            if (planTime == null) {
                continue;
            }
            NurseStationOrderExecuteWriteSlotDto slot = new NurseStationOrderExecuteWriteSlotDto();
            slot.setOrderId(item.getRequestId());
            slot.setOrderType(item.getOrderType());
            slot.setPlanTime(planTime);
            skipItems.add(slot);
        }
    }

    /**
     * 批量装载本批药品读模型。
     *
     * @param items 停嘱校对锚点
     * @return id → 药品列表读模型
     */
    private Map<Long, MedicationRequestListQueryModel> loadMedicationModels(
        List<NurseStationOrderAuditWriteItemDto> items) {
        Set<Long> ids = new LinkedHashSet<>();
        // 只收集本批药嘱 id，一次 IN 查询避免 N+1
        for (NurseStationOrderAuditWriteItemDto item : items) {
            if (item == null || item.getRequestId() == null) {
                continue;
            }
            if (!ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
                continue;
            }
            ids.add(item.getRequestId());
        }
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MedicationRequestListQueryModel> rows =
            medicationRequestQueryService.listMedicationRequestListModelsByIds(ids);
        Map<Long, MedicationRequestListQueryModel> byId = new HashMap<>();
        if (rows == null) {
            return byId;
        }
        for (MedicationRequestListQueryModel row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            byId.put(row.getId(), row);
        }
        return byId;
    }

    /**
     * 批量装载本批诊疗读模型。
     *
     * @param items 停嘱校对锚点
     * @return id → 诊疗列表读模型
     */
    private Map<Long, ServiceRequestListModel> loadServiceModels(
        List<NurseStationOrderAuditWriteItemDto> items) {
        Map<Long, ServiceRequestListModel> byId = new HashMap<>();
        // 诊疗 QS 无 ids 批量接口，与 AUD 校对写路径同构按条装载
        for (NurseStationOrderAuditWriteItemDto item : items) {
            if (item == null || item.getRequestId() == null) {
                continue;
            }
            if (!ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(item.getOrderType())) {
                continue;
            }
            ServiceRequestListModel row = serviceRequestQueryService.getListModelById(item.getRequestId());
            if (row == null) {
                continue;
            }
            byId.put(item.getRequestId(), row);
        }
        return byId;
    }

    /**
     * 从已装载读模型收集就诊主键。
     *
     * @param items   停嘱校对锚点
     * @param medById 药品读模型
     * @param svcById 诊疗读模型
     * @return 就诊主键集合
     */
    private Set<Long> collectEncounterIds(
        List<NurseStationOrderAuditWriteItemDto> items,
        Map<Long, MedicationRequestListQueryModel> medById,
        Map<Long, ServiceRequestListModel> svcById) {
        Set<Long> encounterIds = new LinkedHashSet<>();
        for (NurseStationOrderAuditWriteItemDto item : items) {
            if (item == null || item.getRequestId() == null) {
                continue;
            }
            if (ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
                MedicationRequestListQueryModel medRow = medById.get(item.getRequestId());
                if (medRow != null && medRow.getEncounterId() != null) {
                    encounterIds.add(medRow.getEncounterId());
                }
                continue;
            }
            if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(item.getOrderType())) {
                ServiceRequestListModel svcRow = svcById.get(item.getRequestId());
                if (svcRow != null && svcRow.getEncounterId() != null) {
                    encounterIds.add(svcRow.getEncounterId());
                }
            }
        }
        return encounterIds;
    }

    /**
     * 按就诊批量装载 Procedure，再按 request 键分组。
     *
     * @param encounterIds 就诊主键
     * @return requestTable+requestId → Procedure 列表
     */
    private Map<String, List<Procedure>> loadProceduresByRequestKey(Set<Long> encounterIds) {
        Map<String, List<Procedure>> grouped = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return grouped;
        }
        List<Procedure> all = procedureQueryService.listByEncounterIds(encounterIds);
        if (all == null || all.isEmpty()) {
            return grouped;
        }
        for (Procedure procedure : all) {
            if (procedure == null || procedure.getRequestId() == null
                || StringUtils.isEmpty(procedure.getRequestTable())) {
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
