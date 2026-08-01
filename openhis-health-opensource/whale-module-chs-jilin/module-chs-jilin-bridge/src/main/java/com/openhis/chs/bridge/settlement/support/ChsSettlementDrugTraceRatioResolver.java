/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.settlement.support;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.custom.tracenumber.queries.model.TraceUnitSnapshotQueryModel;
import com.openhis.health.domain.custom.tracenumber.service.TraceNumberQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

/**
 * 结算药品追溯比例解析（DD §4.3）：收费行 → 药品申请/发放批号 → 主表快照优先，否则药典。
 *
 * <p>收费行本身无批号字段；须经 {@code service_table/service_id}（及 dispense 锚点）回查
 * {@link MedicationRequest}/{@link MedicationDispense} 取得批号后再查主表。
 * 本类位于 chs-jilin bridge，经 health-domain QueryService 读跨聚合数据，禁止直注 Repository。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsSettlementDrugTraceRatioResolver {

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final TraceNumberQueryService traceNumberQueryService;

    /**
     * 预加载本批收费行对应的批号（经申请/发放），供循环内解析主表比例。
     *
     * <p>仅做批号批处理；主表快照在 {@link #resolve} 按品+批惰性缓存，因套餐子项药品 id
     * 来自上传 slice 而非收费行 {@code product_id}。</p>
     *
     * @param chargeItems 结算收费明细；可空
     * @return 批号上下文；空入参亦返回空上下文
     */
    public SettlementDrugTraceBatchContext prepare(List<ChargeItem> chargeItems) {
        Map<Long, String> lotByChargeItemId = new HashMap<>();
        // Gate：无收费行则无需查申请/发放
        if (chargeItems == null || chargeItems.isEmpty()) {
            return new SettlementDrugTraceBatchContext(lotByChargeItemId, new HashMap<>());
        }

        Set<Long> medReqIds = new HashSet<>();
        Set<Long> dispenseIds = new HashSet<>();
        // Pre：先收齐收费行上的申请/发放锚点，避免循环内单条查库
        for (ChargeItem item : chargeItems) {
            // 跳过空行，避免 NPE
            if (item == null) {
                continue;
            }
            this.collectAnchorIds(item, medReqIds, dispenseIds);
        }

        Map<Long, MedicationDispense> dispenseById = new HashMap<>();
        List<MedicationDispense> directDispenses =
            this.medicationDispenseQueryService.listMedicationDispensesByIds(dispenseIds);
        // Loop：显式发放锚点入库，并回填其申请 id 以便二次拉同申请发放
        for (MedicationDispense dispense : directDispenses) {
            // 无效发放行不入 map
            if (dispense == null || dispense.getId() == null) {
                continue;
            }
            dispenseById.put(dispense.getId(), dispense);
            // 发放挂申请：后续用申请批拉兄弟发放补批号
            if (dispense.getMedReqId() != null) {
                medReqIds.add(dispense.getMedReqId());
            }
        }

        Map<Long, MedicationRequest> requestById = new HashMap<>();
        List<MedicationRequest> requests = this.medicationRequestQueryService.listByIds(medReqIds);
        // Loop：申请实体入 map，供批号回落
        for (MedicationRequest request : requests) {
            // 无效申请行不入 map
            if (request == null || request.getId() == null) {
                continue;
            }
            requestById.put(request.getId(), request);
        }

        // Pre：申请锚点下再拉发放，批号以发放为准（开立时申请常无批号）
        Map<Long, List<MedicationDispense>> dispensesByMedReqId = new HashMap<>();
        List<MedicationDispense> byReq = this.medicationDispenseQueryService.listByMedReqIds(medReqIds);
        for (MedicationDispense dispense : byReq) {
            // 无申请主键无法归桶
            if (dispense == null || dispense.getMedReqId() == null) {
                continue;
            }
            // 尚未收录的发放补入 id map（与显式锚点去重）
            if (dispense.getId() != null && !dispenseById.containsKey(dispense.getId())) {
                dispenseById.put(dispense.getId(), dispense);
            }
            List<MedicationDispense> bucket = dispensesByMedReqId.get(dispense.getMedReqId());
            // 首次见到该申请：新建桶
            if (bucket == null) {
                bucket = new ArrayList<>();
                dispensesByMedReqId.put(dispense.getMedReqId(), bucket);
            }
            bucket.add(dispense);
        }

        // Asm：逐收费行解析批号写入上下文
        for (ChargeItem item : chargeItems) {
            // 无主键无法与结算循环对齐
            if (item == null || item.getId() == null) {
                continue;
            }
            String lot = this.resolveLotNumber(item, requestById, dispenseById, dispensesByMedReqId);
            // 未配药/无批号：本行结算只能回落药典，不写 map
            if (isEmpty(lot)) {
                continue;
            }
            lotByChargeItemId.put(item.getId(), lot.trim());
        }
        return new SettlementDrugTraceBatchContext(lotByChargeItemId, new HashMap<>());
    }

    /**
     * 解析单行贴码单位与比例：主表（品+批）优先，否则药典入参。
     *
     * @param chargeItem              收费行
     * @param medicationDefinitionId  药品定义主键（来自上传 slice 产品，可空）
     * @param catalogTraceUnitCode    药典贴码单位
     * @param catalogTraceUnitRatio   药典贴码→拆零比
     * @param context                 {@link #prepare} 结果
     * @return 单位+比例（可能仍为空，由计数 Support 按缺比例计 0）
     */
    public ResolvedDrugTraceUnit resolve(
        ChargeItem chargeItem,
        Long medicationDefinitionId,
        String catalogTraceUnitCode,
        BigDecimal catalogTraceUnitRatio,
        SettlementDrugTraceBatchContext context
    ) {
        String catalogUnit = null;
        // 药典单位非空才 trim，避免空串当贴码单位
        if (isNotEmpty(catalogTraceUnitCode)) {
            catalogUnit = catalogTraceUnitCode.trim();
        }
        BigDecimal catalogRatio = catalogTraceUnitRatio;
        // Gate：缺收费行或上下文，无法查批号，只能药典
        if (chargeItem == null || chargeItem.getId() == null || context == null) {
            return new ResolvedDrugTraceUnit(catalogUnit, catalogRatio);
        }
        // Gate：无药品定义主键，无法按品+批查主表
        if (medicationDefinitionId == null) {
            return new ResolvedDrugTraceUnit(catalogUnit, catalogRatio);
        }
        String lot = context.lotByChargeItemId().get(chargeItem.getId());
        // 无批号：无法命中主表，直接药典
        if (isEmpty(lot)) {
            return new ResolvedDrugTraceUnit(catalogUnit, catalogRatio);
        }
        String mapKey = this.traceNumberQueryService.buildItemLotMasterExistsMapKey(
            TableNameConstants.MED_MEDICATION_DEFINITION,
            medicationDefinitionId,
            lot
        );
        TraceUnitSnapshotQueryModel snapshot = context.snapshotCache().get(mapKey);
        // 同品批未查过：拉主表并写入缓存（含 empty 占位，避免重复查库）
        if (snapshot == null && !context.snapshotCache().containsKey(mapKey)) {
            Optional<TraceUnitSnapshotQueryModel> loaded =
                this.traceNumberQueryService.findTraceUnitSnapshotByItemLot(
                    TableNameConstants.MED_MEDICATION_DEFINITION,
                    medicationDefinitionId,
                    lot
                );
            // 主表命中合法快照
            if (loaded.isPresent()) {
                snapshot = loaded.get();
                context.snapshotCache().put(mapKey, snapshot);
            } else {
                // 已查无：占位 null，防止同键反复打库
                context.snapshotCache().put(mapKey, null);
            }
        }
        // 主表有合法快照：单位与比例都优先主表
        if (snapshot != null) {
            String masterUnit = catalogUnit;
            // 主表单位非空则覆盖药典单位（分支 A 同阶判断依赖）
            if (isNotEmpty(snapshot.getTraceUnitCode())) {
                masterUnit = snapshot.getTraceUnitCode().trim();
            }
            BigDecimal masterRatio = catalogRatio;
            // 主表比例为正才覆盖（非法快照仍回落药典）
            if (snapshot.getTraceUnitRatio() != null
                && snapshot.getTraceUnitRatio().compareTo(BigDecimal.ZERO) > 0) {
                masterRatio = snapshot.getTraceUnitRatio();
            }
            return new ResolvedDrugTraceUnit(masterUnit, masterRatio);
        }
        // 主表无快照：药典兜底
        return new ResolvedDrugTraceUnit(catalogUnit, catalogRatio);
    }

    /**
     * 从收费行收集申请/发放主键。
     *
     * @param item        收费行
     * @param medReqIds   申请主键集合（出参）
     * @param dispenseIds 发放主键集合（出参）
     */
    private void collectAnchorIds(ChargeItem item, Set<Long> medReqIds, Set<Long> dispenseIds) {
        String serviceTable = item.getServiceTable();
        Long serviceId = item.getServiceId();
        // service 锚点齐全才识别申请/发放表
        if (isNotEmpty(serviceTable) && serviceId != null) {
            String table = serviceTable.trim();
            // 药品申请锚点（门诊/住院收费主路径）
            if (TableNameConstants.MED_MEDICATION_REQUEST.equals(table)) {
                medReqIds.add(serviceId);
            }
            // 发放作 service 锚点
            if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(table)) {
                dispenseIds.add(serviceId);
            }
        }
        String dispenseTable = item.getDispenseTable();
        Long dispenseId = item.getDispenseId();
        // 发放作 dispense_* 副锚点（与 service 可能并存）
        if (isNotEmpty(dispenseTable)
            && TableNameConstants.MED_MEDICATION_DISPENSE.equals(dispenseTable.trim())
            && dispenseId != null) {
            dispenseIds.add(dispenseId);
        }
    }

    /**
     * 解析收费行对应批号：发放优先，再回落申请。
     *
     * @param item                 收费行
     * @param requestById          申请 map
     * @param dispenseById         发放 map
     * @param dispensesByMedReqId  申请→发放列表
     * @return 批号；无法解析时 null
     */
    private String resolveLotNumber(
        ChargeItem item,
        Map<Long, MedicationRequest> requestById,
        Map<Long, MedicationDispense> dispenseById,
        Map<Long, List<MedicationDispense>> dispensesByMedReqId
    ) {
        // 1) 显式 dispense_id：配药后批号最准
        if (isNotEmpty(item.getDispenseTable())
            && TableNameConstants.MED_MEDICATION_DISPENSE.equals(item.getDispenseTable().trim())
            && item.getDispenseId() != null) {
            MedicationDispense dispense = dispenseById.get(item.getDispenseId());
            String fromDispense = this.lotFromDispense(dispense);
            // 该发放已写批号则直接用
            if (isNotEmpty(fromDispense)) {
                return fromDispense;
            }
        }
        // 2) service 即发放
        if (isNotEmpty(item.getServiceTable())
            && TableNameConstants.MED_MEDICATION_DISPENSE.equals(item.getServiceTable().trim())
            && item.getServiceId() != null) {
            MedicationDispense dispense = dispenseById.get(item.getServiceId());
            String fromDispense = this.lotFromDispense(dispense);
            // 发放自身有批号
            if (isNotEmpty(fromDispense)) {
                return fromDispense;
            }
            // 发放无批号：回落到同申请下其它发放/申请批号
            if (dispense != null && dispense.getMedReqId() != null) {
                return this.lotFromRequestAndSiblingDispenses(
                    dispense.getMedReqId(), requestById, dispensesByMedReqId);
            }
        }
        // 3) service 即药品申请（门诊/住院收费主路径）
        if (isNotEmpty(item.getServiceTable())
            && TableNameConstants.MED_MEDICATION_REQUEST.equals(item.getServiceTable().trim())
            && item.getServiceId() != null) {
            return this.lotFromRequestAndSiblingDispenses(
                item.getServiceId(), requestById, dispensesByMedReqId);
        }
        // 非药品收费锚点：无批号
        return null;
    }

    /**
     * 申请批号：同申请下发放批号优先，否则申请自身批号。
     *
     * @param medReqId            申请主键
     * @param requestById         申请 map
     * @param dispensesByMedReqId 申请→发放
     * @return 批号；皆空时 null
     */
    private String lotFromRequestAndSiblingDispenses(
        Long medReqId,
        Map<Long, MedicationRequest> requestById,
        Map<Long, List<MedicationDispense>> dispensesByMedReqId
    ) {
        List<MedicationDispense> siblings = dispensesByMedReqId.get(medReqId);
        // 已有发放：优先取发放批号（开立时常未回写申请）
        if (siblings != null) {
            for (MedicationDispense sibling : siblings) {
                String lot = this.lotFromDispense(sibling);
                // 取首个非空发放批号（多段发放同批号为主）
                if (isNotEmpty(lot)) {
                    return lot;
                }
            }
        }
        MedicationRequest request = requestById.get(medReqId);
        // 申请不存在（已删或未加载）
        if (request == null) {
            return null;
        }
        // 申请亦无批号（结算早于配药常见）
        if (isEmpty(request.getLotNumber())) {
            return null;
        }
        return request.getLotNumber().trim();
    }

    /**
     * 读取发放批号。
     *
     * @param dispense 发放行
     * @return 批号；空则 null
     */
    private String lotFromDispense(MedicationDispense dispense) {
        // 发放缺失
        if (dispense == null) {
            return null;
        }
        // 发放尚未写批号
        if (isEmpty(dispense.getLotNumber())) {
            return null;
        }
        return dispense.getLotNumber().trim();
    }

    /**
     * 一批结算收费行的批号缓存；主表快照按品+批惰性写入。
     *
     * @param lotByChargeItemId 收费行 id → 批号
     * @param snapshotCache     品批键 → 主表快照（null 表示已查无）
     */
    public record SettlementDrugTraceBatchContext(
        Map<Long, String> lotByChargeItemId,
        Map<String, TraceUnitSnapshotQueryModel> snapshotCache
    ) {
    }

    /**
     * 单行最终采用的贴码单位与比例。
     *
     * @param traceUnitCode  贴码单位
     * @param traceUnitRatio 贴码→拆零比
     */
    public record ResolvedDrugTraceUnit(
        String traceUnitCode,
        BigDecimal traceUnitRatio
    ) {
    }
}
