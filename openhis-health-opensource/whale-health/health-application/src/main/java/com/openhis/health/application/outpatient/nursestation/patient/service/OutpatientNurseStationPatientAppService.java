/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.nursestation.patient.service;

import com.openhis.health.application.outpatient.nursestation.patient.dto.NurseStationPatientQueueItemDto;
import com.openhis.health.application.outpatient.nursestation.patient.mapper.OutpatientNurseStationPatientMapper;
import com.openhis.health.application.outpatient.nursestation.patient.request.PageNurseStationPatientQueueRequest;
import com.openhis.health.application.outpatient.nursestation.patient.response.PageNurseStationPatientQueueResponse;
import com.openhis.health.application.outpatient.nursestation.treatment.support.AdviceChargeAnchorRollupHelper;
import com.openhis.health.application.shared.advice.AdviceChargeDisplayStatusAssembler;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.financial.EncounterContractDisplayAssembler;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.share.enums.OrganizationClassification;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.NurseStationOutpatientQueueQuery;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseListModel;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊护士站患者队列应用编排（IF-NUR-01）。
 *
 * <p>接入说明：由 AppServiceControllerRegistrar 动态注册为
 * {@code POST /api/app/outpatientNurseStationPatient/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientNurseStationPatientAppService {

    private final EncounterContextReader EncounterContextReader;
    private final OutpatientNurseStationPatientMapper outpatientNurseStationPatientMapper;
    /** 费用性质展示：有医保账户优先合同名，供患者卡片区分医保/自费。 */
    private final EncounterContractDisplayAssembler encounterContractDisplayAssembler;
    /** 队列红点：本页耗材请求（已收费口径）。 */
    private final DeviceRequestQueryService deviceRequestQueryService;
    /** 队列红点：本页耗材发放态。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    /** 队列红点：本页收费明细（BR-NUR-018 排除退费态）。 */
    private final ChargeItemQueryService chargeItemQueryService;
    /** 队列红点：收费锚点归并时药品发放 id→申请 id（与处置单同源）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** 队列红点：耗材库位→组织，排除 TECH 耗材房。 */
    private final LocationQueryService locationQueryService;
    /** 队列红点：组织分类判定 TECH。 */
    private final OrganizationQueryService organizationQueryService;

    /**
     * IF-NUR-01：分页查询门诊护士站患者队列。
     *
     * <p>筛选：可选开立科室 {@code orgId}（空=全部）、就诊开始时间区间、关键字、
     * 可选 {@code pendingDeviceOnly}（BR-NUR-017，分页前收窄）。
     * 行内回填 {@code contractDisplay} 与 {@code hasPendingDevice}。</p>
     *
     * @param request 队列筛选与分页条件
     * @return 当前页队列与总数
     */
    public PageNurseStationPatientQueueResponse pageNurseStationPatientQueue(PageNurseStationPatientQueueRequest request) {
        // Gate：请求体非空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求体不能为空");
        }
        // Pre：映射护士站门诊队列分页查询（含可选开立科室、仅待发耗材）
        NurseStationOutpatientQueueQuery queueQuery =
            this.outpatientNurseStationPatientMapper.toNurseStationOutpatientQueueQuery(request);
        // Pre：域侧 BR-NUR-001 准入 + 可选 organization_id / 待发耗材收窄
        Page<OutpatientEncounterContextQueryModel> page =
            this.EncounterContextReader.pageNurseStationOutpatientQueue(queueQuery);
        // Asm：读模型 → 队列 Response
        PageNurseStationPatientQueueResponse resp =
            this.outpatientNurseStationPatientMapper.toPageNurseStationPatientQueueResponse(page);
        // Asm：本页就诊批量回填费用性质（有医保账户优先，禁止前端写死自费）
        this.applyContractDisplayLabels(resp);
        // Asm：本页就诊批量标记是否仍有待发放耗材，供左侧队列卡片提醒
        this.applyPendingDeviceFlags(resp);
        return resp;
    }

    /**
     * 门诊护士站队列：按就诊优先合同批量回填 {@code contractDisplay}。
     *
     * @param resp 分页响应
     */
    private void applyContractDisplayLabels(PageNurseStationPatientQueueResponse resp) {
        // 空页无需查合同
        if (resp == null || resp.getContent() == null || resp.getContent().isEmpty()) {
            return;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        // 收集本页就诊主键，供一次批量解析费用性质
        for (NurseStationPatientQueueItemDto row : resp.getContent()) {
            // 跳过无就诊主键的脏行
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        // 本页无有效就诊则保持 Mapper 空合同名
        if (encounterIds.isEmpty()) {
            return;
        }
        Map<Long, String> contractDisplayByEncounterId =
            encounterContractDisplayAssembler.mapContractDisplayByEncounterIds(encounterIds);
        for (NurseStationPatientQueueItemDto row : resp.getContent()) {
            // 无就诊主键无法对齐合同 Map
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            row.setContractDisplay(contractDisplayByEncounterId.get(row.getEncounterId()));
        }
    }

    /**
     * 门诊护士站队列：按本页就诊批量回填 {@code hasPendingDevice}。
     *
     * <p>口径对齐处置单侧栏待发放：已完成非退单耗材请求，且收费仍为有效计费态（PLANNED/SETTLED），
     * 且无发放单或仍有待发放态发放行；退费作废 {@code DECLINED} 等终态不计待发（BR-NUR-017 / BR-NUR-018）。</p>
     *
     * @param resp 分页响应
     */
    private void applyPendingDeviceFlags(PageNurseStationPatientQueueResponse resp) {
        // Gate：空页无需查耗材请求/发放单
        if (resp == null || resp.getContent() == null || resp.getContent().isEmpty()) {
            return;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        // 只收集本页有效就诊，避免超大 IN
        for (NurseStationPatientQueueItemDto row : resp.getContent()) {
            // 跳过无就诊主键的脏行，避免 IN 查询带入 null
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        // Gate：本页无有效就诊主键则跳过耗材查询
        if (encounterIds.isEmpty()) {
            return;
        }
        Set<Long> pendingEncounterIds = this.collectPendingDeviceEncounterIds(encounterIds);
        // 按就诊回填红点，未命中保持 Mapper 缺省 false
        for (NurseStationPatientQueueItemDto row : resp.getContent()) {
            // 无就诊主键无法对齐发放集合
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            // 命中待发放耗材集合则点亮红点，否则保持 Mapper 默认 false
            row.setHasPendingDevice(pendingEncounterIds.contains(row.getEncounterId()));
        }
    }

    /**
     * 按已收费耗材请求 + 发放态 + 有效计费态，收集仍有待发放耗材的就诊主键。
     *
     * @param encounterIds 本页就诊主键
     * @return 待发耗材就诊集合
     */
    private Set<Long> collectPendingDeviceEncounterIds(LinkedHashSet<Long> encounterIds) {
        LinkedHashSet<Long> pendingEncounterIds = new LinkedHashSet<>();
        // Pre：本页耗材请求/发放/收费一次 IN（分批），禁止按行 N+1
        List<DeviceRequestListModel> deviceRequests =
            this.deviceRequestQueryService.listDeviceRequestsByEncounterIds(encounterIds);
        List<DeviceDispenseListModel> deviceDispenses =
            this.deviceDispenseQueryService.listDeviceDispensesByEncounterIds(encounterIds);
        ChargeItemQueryCommand chargeCmd = new ChargeItemQueryCommand();
        chargeCmd.setEncounterIds(encounterIds);
        List<ChargeItem> chargeItems = this.chargeItemQueryService.listChargeItemsByEncounterIds(chargeCmd);
        Map<String, AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation> chargeByAnchor =
            AdviceChargeAnchorRollupHelper.buildRequestAnchorChargeIndex(
                chargeItems, this.medicationDispenseQueryService, this.deviceDispenseQueryService);
        Map<Long, List<DeviceDispenseListModel>> dispensesByRequestId = new HashMap<>();
        // 按耗材请求聚发放行，供后续判定该请求是否仍待发放
        for (DeviceDispenseListModel dispense : deviceDispenses) {
            // 无请求锚点的发放行无法对齐处置单
            if (dispense == null || dispense.getDeviceReqId() == null) {
                continue;
            }
            List<DeviceDispenseListModel> bucket = dispensesByRequestId.get(dispense.getDeviceReqId());
            // 按耗材请求聚发放行，供后续判定该请求是否仍待发放
            if (bucket == null) {
                bucket = new ArrayList<>();
                dispensesByRequestId.put(dispense.getDeviceReqId(), bucket);
            }
            bucket.add(dispense);
        }
        // 逐条已收费非退单请求：须仍有效计费且无发放或仍有待发态则点亮该就诊
        Set<Long> deviceLocationIds = new LinkedHashSet<>();
        for (DeviceRequestListModel request : deviceRequests) {
            if (request != null && request.getLocationId() != null) {
                deviceLocationIds.add(request.getLocationId());
            }
        }
        Map<Long, Location> locationById =
            this.locationQueryService.loadLocationEntityMapByIds(deviceLocationIds);
        Set<Long> orgIds = new LinkedHashSet<>();
        for (Location location : locationById.values()) {
            if (location != null && location.getOrganizationId() != null) {
                orgIds.add(location.getOrganizationId());
            }
        }
        Map<Long, Organization> orgById = this.organizationQueryService.mapByIds(orgIds);
        for (DeviceRequestListModel request : deviceRequests) {
            // 非处置单纳入口径的请求不点亮红点
            if (!this.isNurseStationDeviceRequestEligible(request)) {
                continue;
            }
            // 业务：TECH 耗材房发放由医技站处理，护士站队列红点跳过
            if (this.isTechDeviceLocation(request.getLocationId(), locationById, orgById)) {
                continue;
            }
            // Skip：收费已退费/无有效计费（对齐侧栏 BR-NUR-018）
            if (!this.hasActiveBillableChargeForDeviceRequest(request.getId(), chargeByAnchor)) {
                continue;
            }
            // 该请求下无发放或仍有待发放行则标记就诊
            if (this.hasNurseStationPendingDeviceDispense(request, dispensesByRequestId.get(request.getId()))) {
                pendingEncounterIds.add(request.getEncounterId());
            }
        }
        return pendingEncounterIds;
    }

    /**
     * 耗材请求库位上级是否为 TECH（医技站轴，护士站队列红点排除）。
     *
     * @param locationId   库位主键
     * @param locationById 库位索引
     * @param orgById      组织索引
     * @return TECH 时 true
     */
    private boolean isTechDeviceLocation(
        Long locationId,
        Map<Long, Location> locationById,
        Map<Long, Organization> orgById) {
        if (locationId == null || locationById == null || orgById == null) {
            return false;
        }
        Location location = locationById.get(locationId);
        if (location == null || location.getOrganizationId() == null) {
            return false;
        }
        Organization org = orgById.get(location.getOrganizationId());
        if (org == null || org.getClassEnum() == null) {
            return false;
        }
        return org.getClassEnum() == OrganizationClassification.TECH;
    }

    /**
     * 耗材请求是否仍存在护士站可执行的有效计费汇总态。
     *
     * @param deviceRequestId 耗材申请主键
     * @param chargeByAnchor  收费锚点归并索引
     * @return 有效计费时 true
     */
    private boolean hasActiveBillableChargeForDeviceRequest(
        Long deviceRequestId,
        Map<String, AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation> chargeByAnchor) {
        // Gate：无申请主键无法对齐收费
        if (deviceRequestId == null || chargeByAnchor == null) {
            return false;
        }
        String key = AdviceChargeAnchorRollupHelper.buildChargeAnchorKey(
            TableNameConstants.WKF_DEVICE_REQUEST, deviceRequestId);
        // Gate：无锚点键
        if (key == null) {
            return false;
        }
        AdviceChargeAnchorRollupHelper.RequestAnchorChargeAggregation agg = chargeByAnchor.get(key);
        // 无收费事实：不当待发
        if (agg == null) {
            return false;
        }
        return AdviceChargeDisplayStatusAssembler.isActiveBillableChargeStatus(agg.getChargeStatusEnum());
    }

    /**
     * 判断耗材请求是否纳入护士站处置单（已完成、非退单镜像）。
     *
     * @param request 耗材请求
     * @return 纳入时 true
     */
    private boolean isNurseStationDeviceRequestEligible(DeviceRequestListModel request) {
        // 缺主键或就诊锚点无法回填队列行
        if (request == null || request.getId() == null || request.getEncounterId() == null) {
            return false;
        }
        // 未收费完成的耗材请求不进护士站处置单
        if (!RequestStatus.COMPLETED.getCode().equals(request.getStatusEnum())) {
            return false;
        }
        // 退单镜像行不在侧栏展示
        return request.getRefundDeviceId() == null;
    }

    /**
     * 判断该耗材请求在侧栏是否仍为待发放。
     *
     * @param request   已收费耗材请求
     * @param dispenses 该请求下发放行；可空
     * @return 无发放单或仍有待发放态时 true
     */
    private boolean hasNurseStationPendingDeviceDispense(
        DeviceRequestListModel request,
        List<DeviceDispenseListModel> dispenses) {
        // 无请求则无法判定
        if (request == null) {
            return false;
        }
        // 已收费但尚未生成发放单：侧栏按待发放展示
        if (dispenses == null || dispenses.isEmpty()) {
            return true;
        }
        // 任一发放行仍为待发放则该患者需要提醒
        for (DeviceDispenseListModel dispense : dispenses) {
            // 任一发放行仍为待发放则该患者需要提醒
            if (dispense != null && this.isNurseStationPendingDispenseStatus(dispense.getStatusEnum())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 发放状态是否为护士站侧栏「待发放」（对齐前端 resolveSupplyDispenseStatusCode）。
     *
     * @param statusEnum 发放状态码
     * @return 待发放时 true
     */
    private boolean isNurseStationPendingDispenseStatus(String statusEnum) {
        // 空状态与前端一致回落待发放
        if (StringUtils.isEmpty(statusEnum)) {
            return true;
        }
        String code = statusEnum.trim().toUpperCase();
        // 退费作废/终止/待退：绝非侧栏待发放（BR-NUR-018）
        if (DispenseStatus.DECLINED.getCode().equals(code)
            || DispenseStatus.STOPPED.getCode().equals(code)
            || DispenseStatus.CANCELLED.getCode().equals(code)
            || DispenseStatus.REFUNDED.getCode().equals(code)
            || DispenseStatus.PENDING_REFUND.getCode().equals(code)) {
            return false;
        }
        // 已配药/已发药：侧栏不再标待发放
        if (DispenseStatus.PREPARED.getCode().equals(code)
            || DispenseStatus.COMPLETED.getCode().equals(code)
            || DispenseStatus.PART_COMPLETED.getCode().equals(code)) {
            return false;
        }
        // 草稿/待配药/待发药：护士尚未执行
        if (DispenseStatus.DRAFT.getCode().equals(code)
            || DispenseStatus.PREPARATION.getCode().equals(code)
            || DispenseStatus.IN_PROGRESS.getCode().equals(code)) {
            return true;
        }
        // 其它已发类 hint 视为已执行，其余回落待发放
        if (code.contains("COMPLETED")
            || code.contains("DISPENSED")
            || code.contains("DONE")
            || code.contains("CLOSED")
            || code.contains("FINISHED")
            || code.contains("PREPARED")) {
            return false;
        }
        // 未识别状态按待发放，避免漏提醒
        return true;
    }
}
