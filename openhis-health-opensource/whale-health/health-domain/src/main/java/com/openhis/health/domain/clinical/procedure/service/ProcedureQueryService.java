/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.procedure.service;

import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.entity.ProcedurePerformer;
import com.openhis.health.domain.clinical.procedure.queries.OrderExecutionSheetPagePredicateSupport;
import com.openhis.health.domain.clinical.procedure.queries.OrderExecutionSheetPageQuery;
import com.openhis.health.domain.clinical.procedure.queries.ProcedureProjectionQuery;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureCompletableCheckQueryModel;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureDetailQueryModel;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedurePerformerQueryModel;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureProjectionQueryModel;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.repository.ProcedurePerformerRepository;
import com.openhis.health.domain.clinical.procedure.repository.ProcedureRepository;
import com.openhis.health.domain.share.enums.EventStatus;
import com.alibaba.fastjson2.JSON;
import org.springframework.data.domain.Page;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.openhis.health.domain.clinical.procedure.queries.NurseWorkloadPageQuery;
import com.openhis.health.domain.clinical.procedure.queries.model.NurseExecutionCategorySummaryQueryModel;
import com.openhis.health.domain.clinical.procedure.queries.model.NurseExecutionWorkloadDetailQueryModel;
import com.openhis.health.domain.clinical.procedure.support.NurseWorkloadProcedureReportCriteriaSupport;
import com.openhis.health.domain.clinical.procedure.support.ProcedureNetExecutionFilterSupport;
import com.openhis.health.domain.share.constants.ClinicalProcedureConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.NurseWorkloadConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.utils.IdInQueryChunkSupport;

import jakarta.persistence.criteria.Predicate;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 诊疗执行（Procedure）记录读侧查询服务。
 *
 * <p>职责：按来源请求加载执行记录、分页投影、详情（含执行人轨迹）及可完成性校验。</p>
 *
 * <p>边界：只读；仅依赖本聚合 {@link ProcedureRepository} / {@link ProcedurePerformerRepository}。
 * 护士工作量（AS-RPT-002）{@code category_enum} 由
 * {@link com.openhis.health.application.report.nurseworkload.support.NurseWorkloadLoader} 预解析写入 query（B.6.4），
 * 见 DM-CLN-004 §6.2.10～6.2.11。</p>
 *
 * <p>部分方法对缺失主键返回 {@code null} 或失败原因模型，而非统一抛异常。</p>
 *
 * <p>聚合边界：仅本聚合 {@link ProcedureRepository}、{@link ProcedurePerformerRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class ProcedureQueryService {
    private final ProcedureRepository procedureRepository;
    private final ProcedurePerformerRepository performerRepository;
    private final ProcedureNetExecutionFilterSupport procedureNetExecutionFilterSupport;

    /**
     * 按来源请求查询执行记录。
     *
     * @param requestId 来源请求标识
     * @param requestTable 来源请求表名
     * @return 命中列表；无命中时返回空集合
     */
    public List<ProcedureRecordQueryModel> getProcedureRecords(Long requestId, String requestTable) {
        List<Procedure> entities = procedureRepository.findByRequestIdAndRequestTable(requestId, requestTable);
        List<ProcedureRecordQueryModel> models = new ArrayList<>();
        for (Procedure entity : entities) {
            models.add(this.toRecordModel(entity));
        }
        return models;
    }

    /**
     * 按就诊批量加载执行记录并按 request 锚点索引（护士站执行列表禁 N+1；IN 分批）。
     *
     * <p>锚点 = {@code requestTable + SOH + requestId}，与执行列表展槽归并口径一致。</p>
     *
     * @param encounterIds 就诊主键集合
     * @return 锚点 → Procedure 读模型列表；空入参返回空 Map
     */
    public Map<String, List<ProcedureRecordQueryModel>> mapProcedureRecordsByRequestAnchorForEncounters(
        Collection<Long> encounterIds) {
        Map<String, List<ProcedureRecordQueryModel>> byAnchor = new HashMap<>();
        List<Long> idList = IdInQueryChunkSupport.distinctNonNullIds(encounterIds);
        if (idList.isEmpty()) {
            return byAnchor;
        }
        // Pre：超大 IN 分批，避免单次参数过长
        List<Procedure> entities = IdInQueryChunkSupport.loadByIdInChunks(
            idList,
            chunkIds -> procedureRepository.findByEncounterIdIn(chunkIds));
        char separator = GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR;
        for (Procedure entity : entities) {
            if (entity == null || entity.getRequestId() == null || StringUtils.isEmpty(entity.getRequestTable())) {
                continue;
            }
            String anchor = entity.getRequestTable() + separator + entity.getRequestId();
            List<ProcedureRecordQueryModel> bucket = byAnchor.get(anchor);
            if (bucket == null) {
                bucket = new ArrayList<>();
                byAnchor.put(anchor, bucket);
            }
            bucket.add(this.toRecordModel(entity));
        }
        return byAnchor;
    }

    /**
     * 按就诊主键集合批量查询执行记录（只读；供应用层按自然日统计净执行等编排）。
     *
     * @param encounterIds 就诊主键集合
     * @return 执行记录列表；入参为空时返回空列表
     */
    public List<Procedure> listByEncounterIds(Collection<Long> encounterIds) {
        Collection<Long> idSet = this.distinctNonNullEncounterIds(encounterIds);
        if (idSet.isEmpty()) {
            return Collections.emptyList();
        }
        return procedureRepository.findByEncounterIdIn(idSet);
    }

    /**
     * 按主键集合批量加载执行记录（DM-CLN-004 §6.2.8；AS-IP-NUR-006）。
     *
     * @param procedureIds 执行记录主键集合
     * @return 命中实体列表；入参为空时返回空列表
     */
    public List<Procedure> listByIds(Collection<Long> procedureIds) {
        Collection<Long> idSet = this.distinctNonNullProcedureIds(procedureIds);
        if (idSet.isEmpty()) {
            return Collections.emptyList();
        }
        return procedureRepository.findAllByIdIn(idSet);
    }

    /**
     * 构建就诊维度「净剩 COMPLETED」执行记录的业务时间索引（AS-IP-NUR-006 BR-IP-CDT-003/015）。
     *
     * <p>口径与 {@link #listNetRemainingCompletedProcedureRecords} 一致：已被 {@code CANCEL} 且 {@code refund_id} 指向的
     * {@code COMPLETED} 不再计入；仅药品/诊疗来源表。</p>
     *
     * @param encounterId 就诊主键
     * @return 净剩执行记录主键 → {@code occurrence_time}；无命中时返回空 Map
     */
    public Map<Long, LocalDateTime> mapNetCompletedOccurrenceTimeByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return Collections.emptyMap();
        }
        List<ProcedureRecordQueryModel> netRows = this.listNetRemainingCompletedProcedureRecords(encounterId, null, null);
        Map<Long, LocalDateTime> occurrenceById = new HashMap<>();
        for (ProcedureRecordQueryModel row : netRows) {
            if (row == null || row.getId() == null || row.getOccurrenceTime() == null) {
                continue;
            }
            occurrenceById.put(row.getId(), row.getOccurrenceTime());
        }
        return occurrenceById;
    }

    /**
     * 按诊疗请求主键批量解析净剩 COMPLETED 的客户可见时刻（{@code recorded_time}）。
     *
     * <p>供护士划价已划价列表（IF-IP-PRC-05）与改时（IF-IP-PRC-07）展示轴；按就诊加载净剩行后按
     * {@code request_table=wkf_service_request} 收窄。</p>
     *
     * @param encounterIds      就诊主键集合
     * @param serviceRequestIds 诊疗请求主键集合；空则返回空 Map
     * @return 诊疗请求 id → recorded_time
     */
    public Map<Long, LocalDateTime> mapNetCompletedRecordedTimeByServiceRequestIds(
        Collection<Long> encounterIds,
        Collection<Long> serviceRequestIds) {
        Map<Long, LocalDateTime> recordedByRequestId = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()
            || serviceRequestIds == null || serviceRequestIds.isEmpty()) {
            return recordedByRequestId;
        }
        Set<Long> serviceIdSet = new HashSet<>();
        for (Long serviceRequestId : serviceRequestIds) {
            if (serviceRequestId != null) {
                serviceIdSet.add(serviceRequestId);
            }
        }
        if (serviceIdSet.isEmpty()) {
            return recordedByRequestId;
        }
        String serviceRequestTable = TableNameConstants.WKF_SERVICE_REQUEST;
        for (Long encounterId : this.distinctNonNullEncounterIds(encounterIds)) {
            List<ProcedureRecordQueryModel> netRows =
                this.listNetRemainingCompletedProcedureRecords(encounterId, null, null);
            for (ProcedureRecordQueryModel row : netRows) {
                if (row == null || row.getRequestId() == null || row.getRecordedTime() == null) {
                    continue;
                }
                // 仅诊疗请求表；非目标请求跳过
                if (!serviceRequestTable.equals(row.getRequestTable())) {
                    continue;
                }
                if (!serviceIdSet.contains(row.getRequestId())) {
                    continue;
                }
                recordedByRequestId.put(row.getRequestId(), row.getRecordedTime());
            }
        }
        return recordedByRequestId;
    }

    /**
     * 按诊疗请求解析净剩 COMPLETED 执行记录主键（改时写路径锚定）。
     *
     * @param encounterId     就诊主键
     * @param serviceRequestId 诊疗请求主键
     * @return 净剩 COMPLETED 主键；无则 empty
     */
    public Optional<Long> findNetCompletedProcedureIdByServiceRequest(Long encounterId, Long serviceRequestId) {
        if (encounterId == null || serviceRequestId == null) {
            return Optional.empty();
        }
        List<ProcedureRecordQueryModel> netRows = this.listNetRemainingCompletedProcedureRecords(
            encounterId, serviceRequestId, TableNameConstants.WKF_SERVICE_REQUEST);
        for (ProcedureRecordQueryModel row : netRows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            return Optional.of(row.getId());
        }
        return Optional.empty();
    }

    /**
     * 构建就诊维度「净剩 COMPLETED」执行记录的展示时间索引（AS-IP-NUR-006 双轨展示轴）。
     *
     * <p>口径与 {@link #mapNetCompletedOccurrenceTimeByEncounterId} 同净剩集合；值为
     * {@code recorded_time}（护士认定实际执行时刻）。过滤/排序仍用 occurrence，本索引仅供页面展示。</p>
     *
     * @param encounterId 就诊主键
     * @return 净剩执行记录主键 → {@code recorded_time}；无 recorded 的行不入 Map
     */
    public Map<Long, LocalDateTime> mapNetCompletedRecordedTimeByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return Collections.emptyMap();
        }
        List<ProcedureRecordQueryModel> netRows = this.listNetRemainingCompletedProcedureRecords(encounterId, null, null);
        Map<Long, LocalDateTime> recordedById = new HashMap<>();
        for (ProcedureRecordQueryModel row : netRows) {
            if (row == null || row.getId() == null || row.getRecordedTime() == null) {
                continue;
            }
            recordedById.put(row.getId(), row.getRecordedTime());
        }
        return recordedById;
    }

    /**
     * 按 {@code recorded_time} 左闭右开窗拉取净剩 COMPLETED 诊疗执行（仅本聚合 Repository）。
     *
     * <p>供住院费用明细报表待结业务日轴（客户可见 recorded）；对冲口径与护士工作量同构。
     * 仅 {@code wkf_service_request} 锚点。</p>
     *
     * @param recordedFromInclusive recorded 起（含）
     * @param recordedToExclusive   recorded 止（不含）
     * @return 净剩 COMPLETED 实体；窗非法时返回空列表
     */
    public List<Procedure> listNetCompletedServiceRequestProceduresByRecordedTimeWindow(
        LocalDateTime recordedFromInclusive,
        LocalDateTime recordedToExclusive) {
        // 诊疗执行：request_table = wkf_service_request
        return this.listNetCompletedProceduresByRequestTableAndRecordedTimeWindow(
            TableNameConstants.WKF_SERVICE_REQUEST,
            recordedFromInclusive,
            recordedToExclusive);
    }

    /**
     * 按 {@code recorded_time} 左闭右开窗拉取净剩 COMPLETED 药品执行（仅本聚合 Repository）。
     *
     * <p>供住院费用明细报表待结药品业务日轴；与诊疗同构，锚点 {@code med_medication_request}。
     * 禁止只扫诊疗窗导致待结药品漏报（显示不全）。</p>
     *
     * @param recordedFromInclusive recorded 起（含）
     * @param recordedToExclusive   recorded 止（不含）
     * @return 净剩 COMPLETED 实体；窗非法时返回空列表
     */
    public List<Procedure> listNetCompletedMedicationRequestProceduresByRecordedTimeWindow(
        LocalDateTime recordedFromInclusive,
        LocalDateTime recordedToExclusive) {
        // 药品执行：request_table = med_medication_request
        return this.listNetCompletedProceduresByRequestTableAndRecordedTimeWindow(
            TableNameConstants.MED_MEDICATION_REQUEST,
            recordedFromInclusive,
            recordedToExclusive);
    }

    /**
     * 按指定 {@code request_table} + {@code recorded_time} 左闭右开窗拉取净剩 COMPLETED 执行（仅本聚合 Repository）。
     *
     * <p>对冲口径：同表 {@code CANCEL.refund_id} 指向的 COMPLETED 剔除。净过滤须传入与候选相同的
     * {@code requestTable}（诊疗/药品各自净轴）；禁止误用「仅诊疗 SR」过滤器，否则药品执行会被整表丢弃，
     * 住院费用明细待结轴漏药。</p>
     *
     * @param requestTable          来源请求表持久化名（诊疗/药品）
     * @param recordedFromInclusive recorded 起（含）
     * @param recordedToExclusive   recorded 止（不含）
     * @return 净剩 COMPLETED 实体；表名空或窗非法时返回空列表
     */
    public List<Procedure> listNetCompletedProceduresByRequestTableAndRecordedTimeWindow(
        String requestTable,
        LocalDateTime recordedFromInclusive,
        LocalDateTime recordedToExclusive) {
        if (StringUtils.isEmpty(requestTable)) {
            return Collections.emptyList();
        }
        if (recordedFromInclusive == null || recordedToExclusive == null) {
            return Collections.emptyList();
        }
        if (!recordedFromInclusive.isBefore(recordedToExclusive)) {
            return Collections.emptyList();
        }
        List<Procedure> candidates =
            this.procedureRepository.findByRequestTableAndRecordedTimeGreaterThanEqualAndRecordedTimeLessThanAndIsDeletedFalse(
                requestTable,
                recordedFromInclusive,
                recordedToExclusive);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> completedIds = new LinkedHashSet<>();
        for (Procedure entity : candidates) {
            if (entity != null
                && entity.getId() != null
                && EventStatus.COMPLETED.getCode().equals(entity.getStatusEnum())) {
                completedIds.add(entity.getId());
            }
        }
        List<Procedure> cancelRows = Collections.emptyList();
        if (!completedIds.isEmpty()) {
            // Pre：对冲 CANCEL 须与候选同 request_table（诊疗/药品各自净轴，禁止串表）
            cancelRows = this.procedureRepository.findByRefundIdInAndRequestTableAndIsDeletedFalse(
                completedIds,
                requestTable);
        }
        // Asm：按入参 requestTable 净 COMPLETED；禁止误用仅诊疗 SR 过滤器导致药品执行被整表丢弃
        return this.procedureNetExecutionFilterSupport.filterNetCompletedProceduresByRequestTable(
            candidates,
            cancelRows,
            requestTable);
    }

    /**
     * 就诊主键去重（批量查询入参归一化）。
     *
     * @param encounterIds 原始就诊主键集合
     * @return 非空去重集合
     */
    private Collection<Long> distinctNonNullEncounterIds(Collection<Long> encounterIds) {
        LinkedHashSet<Long> idSet = new LinkedHashSet<>();
        if (encounterIds == null) {
            return idSet;
        }
        for (Long encounterId : encounterIds) {
            if (encounterId != null) {
                idSet.add(encounterId);
            }
        }
        return idSet;
    }

    /**
     * 执行记录主键去重（批量查询入参归一化）。
     *
     * @param procedureIds 原始主键集合
     * @return 非空去重集合
     */
    private Collection<Long> distinctNonNullProcedureIds(Collection<Long> procedureIds) {
        LinkedHashSet<Long> idSet = new LinkedHashSet<>();
        if (procedureIds == null) {
            return idSet;
        }
        for (Long procedureId : procedureIds) {
            if (procedureId != null) {
                idSet.add(procedureId);
            }
        }
        return idSet;
    }

    /**
     * 查询净剩可取消的已完成执行记录（对冲后仍有效的 COMPLETED 行）。
     *
     * <p>规则：同一来源请求下，若存在 {@code CANCEL} 且 {@code refund_id} 指向某条 COMPLETED，则该 COMPLETED 与对应 CANCEL 均不再展示；
     * 仅返回尚未被抵消的 COMPLETED 行。仅包含诊疗/药品来源表，不含耗材链路。</p>
     *
     * @param encounterId  就诊主键
     * @param requestId    可选，缩窄至单条来源请求
     * @param requestTable 与 {@code requestId} 成对传入时的来源表持久化名
     * @return 净剩 COMPLETED 行；无命中时返回空集合
     */
    public List<ProcedureRecordQueryModel> listNetRemainingCompletedProcedureRecords(
        Long encounterId,
        Long requestId,
        String requestTable) {
        List<Procedure> entities;
        if (requestId != null && StringUtils.isNotEmpty(requestTable)) {
            entities = this.procedureRepository.findByEncounterIdAndRequestIdAndRequestTable(
                encounterId, requestId, requestTable);
        } else {
            entities = this.procedureRepository.findByEncounterId(encounterId);
        }
        Set<Long> offsetCompletedIds = new HashSet<>();
        for (Procedure entity : entities) {
            if (entity == null || entity.getId() == null) {
                continue;
            }
            if (!this.isNurseStationProcedureRequestTable(entity.getRequestTable())) {
                continue;
            }
            if (EventStatus.isCancelExecutionStatusCode(entity.getStatusEnum())) {
                if (entity.getRefundId() != null) {
                    offsetCompletedIds.add(entity.getRefundId());
                }
            }
        }
        List<ProcedureRecordQueryModel> netRows = new ArrayList<>();
        for (Procedure entity : entities) {
            if (entity == null || entity.getId() == null) {
                continue;
            }
            if (!this.isNurseStationProcedureRequestTable(entity.getRequestTable())) {
                continue;
            }
            if (!EventStatus.COMPLETED.getCode().equals(entity.getStatusEnum())) {
                continue;
            }
            if (offsetCompletedIds.contains(entity.getId())) {
                continue;
            }
            netRows.add(this.toRecordModel(entity));
        }
        return netRows;
    }

    /**
     * 医嘱执行单分页：仅查 {@code cln_procedure} 主表（富化由应用层 {@code OrderExecutionSheetLoader} 完成）。
     *
     * @param query 分页与过滤入参
     * @return 执行记录实体分页
     */
    public Page<Procedure> pageProceduresForOrderExecutionSheet(OrderExecutionSheetPageQuery query) {
        int springIndex = 0;
        int pageSize = 20;
        if (query != null) {
            springIndex = query.springPageIndex();
            pageSize = query.getBoundedPageSize();
        }
        Pageable pageable = PageRequest.of(
            springIndex, pageSize, Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT)));
        Specification<Procedure> specification = (root, criteriaQuery, cb) -> cb.and(
            OrderExecutionSheetPagePredicateSupport.collectPagePredicates(root, cb, query)
                .toArray(new Predicate[0]));
        return this.procedureRepository.findAll(specification, pageable);
    }

    /**
     * 批量装配执行人列表 JSON（供执行单读侧富化）。
     *
     * @param procedureIds 执行记录主键集合
     * @return 执行记录 id → 执行人 JSON 数组字符串
     */
    public Map<Long, String> mapPerformerListJsonByProcedureIds(Collection<Long> procedureIds) {
        Collection<Long> idSet = this.distinctNonNullProcedureIds(procedureIds);
        if (idSet.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ProcedurePerformer> rows = this.performerRepository.findAllByProcedureIdIn(idSet);
        Map<Long, List<ProcedurePerformer>> grouped = new HashMap<>();
        for (ProcedurePerformer performer : rows) {
            if (performer == null || performer.getProcedureId() == null) {
                continue;
            }
            List<ProcedurePerformer> bucket = grouped.get(performer.getProcedureId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(performer.getProcedureId(), bucket);
            }
            bucket.add(performer);
        }
        Map<Long, String> jsonByProcedureId = new HashMap<>();
        for (Map.Entry<Long, List<ProcedurePerformer>> entry : grouped.entrySet()) {
            jsonByProcedureId.put(entry.getKey(), JSON.toJSONString(entry.getValue()));
        }
        return jsonByProcedureId;
    }

    /**
     * 批量解析各执行记录的主执行人执业者主键（{@code PRIMARY} 优先，否则取首条）。
     *
     * @param procedureIds 执行记录主键集合
     * @return 执行记录 id → 主执行人 {@code adm_practitioner.id}
     */
    public Map<Long, Long> mapPrimaryPractitionerIdByProcedureIds(Collection<Long> procedureIds) {
        Collection<Long> idSet = this.distinctNonNullProcedureIds(procedureIds);
        if (idSet.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ProcedurePerformer> rows = this.performerRepository.findAllByProcedureIdIn(idSet);
        Map<Long, ProcedurePerformer> primaryByProcedureId = new HashMap<>();
        Map<Long, ProcedurePerformer> fallbackByProcedureId = new HashMap<>();
        for (ProcedurePerformer performer : rows) {
            if (performer == null || performer.getProcedureId() == null || performer.getPractitionerId() == null) {
                continue;
            }
            Long procedureId = performer.getProcedureId();
            if (ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_PRIMARY.equals(performer.getFunctionCode())) {
                primaryByProcedureId.put(procedureId, performer);
                continue;
            }
            ProcedurePerformer existingFallback = fallbackByProcedureId.get(procedureId);
            // 无 PRIMARY 时取 id 最小执行人，与护士报表 Criteria 子查询口径一致
            if (existingFallback == null
                || (performer.getId() != null
                    && existingFallback.getId() != null
                    && performer.getId() < existingFallback.getId())) {
                fallbackByProcedureId.put(procedureId, performer);
            }
        }
        Map<Long, Long> practitionerIdByProcedureId = new HashMap<>();
        for (Long procedureId : idSet) {
            ProcedurePerformer chosen = primaryByProcedureId.get(procedureId);
            if (chosen == null) {
                chosen = fallbackByProcedureId.get(procedureId);
            }
            if (chosen != null) {
                practitionerIdByProcedureId.put(procedureId, chosen.getPractitionerId());
            }
        }
        return practitionerIdByProcedureId;
    }

    /**
     * 分页查询执行记录投影。
     *
     * @param query 查询条件（预留）
     * @param pageable 分页参数
     * @return 分页结果
     */
    public Page<ProcedureProjectionQueryModel> queryProcedureProjections(ProcedureProjectionQuery query, Pageable pageable) {
        // DOC-CONFLICT: 当前仓储仅提供 findAll 分页能力，query 条件预留待后续补充规格后下推到 DB。
        Page<Procedure> page = procedureRepository.findAll(pageable);
        List<ProcedureProjectionQueryModel> models = new ArrayList<>();
        for (Procedure entity : page.getContent()) {
            models.add(this.toProjectionModel(entity));
        }
        return new PageImpl<>(models, pageable, page.getTotalElements());
    }

    /**
     * 按主键加载执行记录详情。
     *
     * @param procedureId 执行记录主键
     * @return 详情模型；主键不存在时返回 null（调用方按空对象语义处理）
     */
    public ProcedureDetailQueryModel loadProcedureById(Long procedureId) {
        Procedure entity = procedureRepository.findById(procedureId).orElse(null);
        // 读侧弱依赖：无记录时返回 null，由调用方决定 404 或静默。
        if (entity == null) {
            return null;
        }
        ProcedureDetailQueryModel model = new ProcedureDetailQueryModel();
        model.setProcedure(this.toRecordModel(entity));
        model.setPerformers(this.listPerformerByProcedure(procedureId));
        return model;
    }

    /**
     * 按执行记录查询执行人轨迹。
     *
     * @param procedureId 执行记录主键
     * @return 执行人列表；无命中时返回空集合
     */
    public List<ProcedurePerformerQueryModel> listPerformerByProcedure(Long procedureId) {
        List<ProcedurePerformer> entities = performerRepository.findAllByProcedureId(procedureId);
        List<ProcedurePerformerQueryModel> models = new ArrayList<>();
        for (ProcedurePerformer entity : entities) {
            ProcedurePerformerQueryModel model = new ProcedurePerformerQueryModel();
            model.setId(entity.getId());
            model.setProcedureId(entity.getProcedureId());
            model.setPractitionerId(entity.getPractitionerId());
            model.setFunctionCode(entity.getFunctionCode());
            model.setStartTime(entity.getStartTime());
            model.setEndTime(entity.getEndTime());
            models.add(model);
        }
        return models;
    }

    /**
     * 执行记录可完成性校验。
     *
     * @param procedureId 执行记录主键
     * @return 可完成性结果模型；主键不存在或状态不满足时返回失败原因
     */
    public ProcedureCompletableCheckQueryModel checkProcedureCompletable(Long procedureId) {
        ProcedureCompletableCheckQueryModel model = new ProcedureCompletableCheckQueryModel();
        model.setProcedureId(procedureId);
        Procedure procedure = procedureRepository.findById(procedureId).orElse(null);
        if (procedure == null) {
            model.setPass(Boolean.FALSE);
            model.setFailCode("NOT_FOUND");
            model.setFailReason("执行记录不存在");
            return model;
        }
        model.setStatusEnum(procedure.getStatusEnum());
        // 仅「执行中」允许点完成，避免重复完记或逆序操作。
        if (!EventStatus.IN_PROGRESS.getCode().equals(procedure.getStatusEnum())) {
            model.setPass(Boolean.FALSE);
            model.setFailCode("INVALID_STATUS");
            model.setFailReason("仅执行中状态可完成");
            return model;
        }
        // 完成前至少要有文字小结或报告 JSON 之一，保证病历可追溯。
        boolean contentReady = (procedure.getResultText() != null && !procedure.getResultText().isBlank())
            || (procedure.getReportJson() != null && !procedure.getReportJson().isBlank());
        if (!contentReady) {
            model.setPass(Boolean.FALSE);
            model.setFailCode("RESULT_MISSING");
            model.setFailReason("缺少结果摘要和报告内容");
            return model;
        }
        model.setPass(Boolean.TRUE);
        model.setFailCode("OK");
        model.setFailReason("可完成");
        return model;
    }

    /**
     * 护士诊疗执行工作量按 {@code ServiceRequest.category_enum} 汇总（DM-CLN-004 §6.2.10；AS-RPT-002 IF-RPT-NWL-02）。
     *
     * @param query 须经 {@link com.openhis.health.application.report.nurseworkload.support.NurseWorkloadLoader} 预解析 scope
     * @return 汇总行；无命中时返回空列表
     */
    public List<NurseExecutionCategorySummaryQueryModel> summarizeNurseExecutionWorkloadByCategory(
        NurseWorkloadPageQuery query) {
        // Pre：同窗内净 COMPLETED 候选（仅 wkf_service_request；BR-RPT-NWL-003/004）
        List<Procedure> netRows = this.listNurseExecutionNetCompletedProcedures(query);
        // Asm：按 Support 预解析的 SR.category_enum 分桶计数
        Map<String, NurseExecutionCategorySummaryQueryModel> bucketByCategory = new LinkedHashMap<>();
        Map<Long, String> categoryByServiceRequestId = this.resolveCategoryEnumByServiceRequestId(query);
        for (Procedure entity : netRows) {
            if (entity == null || entity.getRequestId() == null) {
                continue;
            }
            String categoryEnum = categoryByServiceRequestId.get(entity.getRequestId());
            String bucketKey = "";
            if (categoryEnum != null) {
                bucketKey = categoryEnum;
            }
            NurseExecutionCategorySummaryQueryModel summary = bucketByCategory.get(bucketKey);
            if (summary == null) {
                summary = new NurseExecutionCategorySummaryQueryModel();
                summary.setCategoryEnum(categoryEnum);
                summary.setExecutionCount(0L);
                bucketByCategory.put(bucketKey, summary);
            }
            summary.setExecutionCount(summary.getExecutionCount() + 1L);
        }
        return new ArrayList<>(bucketByCategory.values());
    }

    /**
     * 护士诊疗执行工作量明细分页（DM-CLN-004 §6.2.11；AS-RPT-002 IF-RPT-NWL-03）。
     *
     * @param query 预解析 scope + 分页键
     * @return 净 COMPLETED 行分页；按 {@code recorded_time} 倒序
     */
    public Page<NurseExecutionWorkloadDetailQueryModel> pageNurseExecutionWorkloadDetail(
        NurseWorkloadPageQuery query) {
        int pageSize = this.resolveNurseWorkloadPageSize(query);
        int springIndex = 0;
        if (query != null) {
            springIndex = query.springPageIndex();
        }
        // Pre：同窗净 COMPLETED 已排序缓存则内存切片（汇总+明细分页同窗复用）
        if (query != null && query.getCachedNetCompletedProceduresForRequest() != null) {
            List<Procedure> netRows = this.castNetCompletedProcedureCache(query.getCachedNetCompletedProceduresForRequest());
            int fromIndex = springIndex * pageSize;
            Pageable pageable = PageRequest.of(springIndex, pageSize);
            if (fromIndex >= netRows.size()) {
                return new PageImpl<>(Collections.emptyList(), pageable, netRows.size());
            }
            int toIndex = Math.min(fromIndex + pageSize, netRows.size());
            List<NurseExecutionWorkloadDetailQueryModel> pageModels =
                this.toNurseExecutionWorkloadDetailModels(netRows.subList(fromIndex, toIndex), query);
            return new PageImpl<>(pageModels, pageable, netRows.size());
        }
        // Pre：明细分页专用 DB 分页（NOT EXISTS 对冲 + recorded_time 倒序；禁止全窗 findAll 后 subList）
        Pageable pageable = PageRequest.of(
            springIndex,
            pageSize,
            Sort.by(Sort.Direction.DESC, "recordedTime"));
        Specification<Procedure> specification =
            NurseWorkloadProcedureReportCriteriaSupport.buildNetCompletedSpecification(query);
        Page<Procedure> entityPage = this.procedureRepository.findAll(specification, pageable);
        List<NurseExecutionWorkloadDetailQueryModel> pageModels =
            this.toNurseExecutionWorkloadDetailModels(entityPage.getContent(), query);
        return new PageImpl<>(pageModels, pageable, entityPage.getTotalElements());
    }

    /**
     * 护士诊疗执行工作量明细全量列表（汇总页脚金额复用；与分页同口径）。
     *
     * @param query 预解析 scope
     * @return 按 {@code recorded_time} 倒序的净 COMPLETED 明细
     */
    public List<NurseExecutionWorkloadDetailQueryModel> listNurseExecutionWorkloadDetail(
        NurseWorkloadPageQuery query) {
        List<Procedure> netRows = this.listNurseExecutionNetCompletedProcedures(query);
        return this.toNurseExecutionWorkloadDetailModels(netRows, query);
    }

    /**
     * 将净执行 Procedure 切片装配为明细读模型（批量主执行人 + 预解析类目）。
     *
     * @param procedureSlice 已排序的净执行切片
     * @param query          含类目 enrich map
     * @return 明细读模型
     */
    private List<NurseExecutionWorkloadDetailQueryModel> toNurseExecutionWorkloadDetailModels(
        List<Procedure> procedureSlice,
        NurseWorkloadPageQuery query) {
        if (procedureSlice == null || procedureSlice.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> procedureIds = new ArrayList<>();
        for (Procedure entity : procedureSlice) {
            if (entity != null && entity.getId() != null) {
                procedureIds.add(entity.getId());
            }
        }
        Map<Long, Long> practitionerIdByProcedureId = this.mapPrimaryPractitionerIdByProcedureIds(procedureIds);
        Map<Long, String> categoryByServiceRequestId = this.resolveCategoryEnumByServiceRequestId(query);
        List<NurseExecutionWorkloadDetailQueryModel> models = new ArrayList<>(procedureSlice.size());
        for (Procedure entity : procedureSlice) {
            if (entity == null) {
                continue;
            }
            NurseExecutionWorkloadDetailQueryModel model = new NurseExecutionWorkloadDetailQueryModel();
            model.setProcedureId(entity.getId());
            model.setServiceRequestId(entity.getRequestId());
            model.setEncounterId(entity.getEncounterId());
            model.setPatientId(entity.getPatientId());
            model.setRecordedTime(entity.getRecordedTime());
            model.setPractitionerId(practitionerIdByProcedureId.get(entity.getId()));
            if (entity.getRequestId() != null) {
                model.setCategoryEnum(categoryByServiceRequestId.get(entity.getRequestId()));
            }
            models.add(model);
        }
        return models;
    }

    /**
     * 加载护士诊疗执行净 COMPLETED 候选集（BR-RPT-NWL-003～006；仅 {@code wkf_service_request}）。
     *
     * <p>对冲规则与 §6.2.6 一致：{@code CANCEL}+{@code refund_id} 抵消对应 COMPLETED。</p>
     *
     * @param query 预解析查询（含 resolvedEncounterScope；可选 practitionerId / practitionerIds 收窄主执行人）
     * @return 净执行记录
     */
    private List<Procedure> listNurseExecutionNetCompletedProcedures(NurseWorkloadPageQuery query) {
        // Pre：同窗内已物化净执行事实则直接复用（汇总+页脚+卡片禁止重复扫库）
        if (query != null && query.getCachedNetCompletedProceduresForRequest() != null) {
            return this.castNetCompletedProcedureCache(query.getCachedNetCompletedProceduresForRequest());
        }
        // Gate：Support 未写入 scope 或日期无效时直接空集
        if (query == null
            || query.getDateFrom() == null
            || query.getDateTo() == null
            || query.getResolvedEncounterScope() == null
            || query.getResolvedEncounterScope().isEmpty()) {
            return this.storeNetCompletedProcedureCache(query, Collections.emptyList());
        }
        // Pre：与明细分页共用同一套 Criteria（汇总条数 = 明细 total；禁止 Java/SQL 双轨）
        Specification<Procedure> specification =
            NurseWorkloadProcedureReportCriteriaSupport.buildNetCompletedSpecification(query);
        Sort sort = Sort.by(Sort.Direction.DESC, "recordedTime");
        List<Procedure> netRows = this.procedureRepository.findAll(specification, sort);
        return this.storeNetCompletedProcedureCache(query, netRows);
    }

    /**
     * 写入同窗净执行事实缓存并返回同一列表引用。
     *
     * @param query  护士工作量查询
     * @param netRows 净 COMPLETED 实体
     * @return {@code netRows}
     */
    private List<Procedure> storeNetCompletedProcedureCache(
        NurseWorkloadPageQuery query,
        List<Procedure> netRows) {
        List<Procedure> sortedRows = netRows;
        // Pre：同窗缓存前按 recorded_time 倒序排好，分页/全量列表禁止重复 sort
        if (sortedRows != null && sortedRows.size() > 1) {
            sortedRows = new ArrayList<>(sortedRows);
            sortedRows.sort(Comparator.comparing(
                Procedure::getRecordedTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        }
        if (query != null) {
            query.setCachedNetCompletedProceduresForRequest(sortedRows);
        }
        return sortedRows;
    }

    /**
     * 读取同窗净执行事实缓存（类型擦除安全转换）。
     *
     * @param cached 缓存列表
     * @return Procedure 列表
     */
    @SuppressWarnings("unchecked")
    private List<Procedure> castNetCompletedProcedureCache(List<?> cached) {
        if (cached == null || cached.isEmpty()) {
            return Collections.emptyList();
        }
        return (List<Procedure>) cached;
    }

    /**
     * 读取 Support 预解析的 SR 类目 map（B.6.4 跨聚合 enrich 结果）。
     *
     * @param query 护士工作量查询
     * @return serviceRequestId → categoryEnum
     */
    private Map<Long, String> resolveCategoryEnumByServiceRequestId(NurseWorkloadPageQuery query) {
        if (query == null || query.getCategoryEnumByServiceRequestId() == null) {
            return Collections.emptyMap();
        }
        return query.getCategoryEnumByServiceRequestId();
    }

    /**
     * 解析明细分页条数（AS-RPT-002 默认 20，硬上限 1000）。
     *
     * @param query 护士工作量查询
     * @return 每页条数
     */
    private int resolveNurseWorkloadPageSize(NurseWorkloadPageQuery query) {
        if (query == null || query.getPageSize() == null || query.getPageSize() < 1) {
            return NurseWorkloadConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(query.getPageSize(), 1000);
    }

    /**
     * 将执行记录实体映射为列表/详情共用的基础行模型。
     *
     * @param entity 执行记录实体
     * @return 基础记录模型
     */
    private ProcedureRecordQueryModel toRecordModel(Procedure entity) {
        ProcedureRecordQueryModel model = new ProcedureRecordQueryModel();
        model.setId(entity.getId());
        model.setRequestId(entity.getRequestId());
        model.setRequestTable(entity.getRequestTable());
        model.setStatusEnum(entity.getStatusEnum());
        model.setRecordedTime(entity.getRecordedTime());
        model.setRefundId(entity.getRefundId());
        model.setEncounterId(entity.getEncounterId());
        model.setPatientId(entity.getPatientId());
        model.setCategoryEnum(entity.getCategoryEnum());
        model.setOccurrenceTime(entity.getOccurrenceTime());
        model.setRecorderId(entity.getRecorderId());
        model.setResultText(entity.getResultText());
        return model;
    }

    /**
     * 判断来源表是否属于护士站诊疗/药品执行登记（不含耗材发放链路）。
     *
     * @param requestTable 来源请求表持久化名
     * @return 是否为诊疗或药品请求表
     */
    private boolean isNurseStationProcedureRequestTable(String requestTable) {
        if (StringUtils.isEmpty(requestTable)) {
            return false;
        }
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(requestTable)) {
            return true;
        }
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(requestTable)) {
            return true;
        }
        return false;
    }

    /**
     * 将执行记录实体映射为分页投影行。
     *
     * @param entity 执行记录实体
     * @return 投影模型
     */
    private ProcedureProjectionQueryModel toProjectionModel(Procedure entity) {
        ProcedureProjectionQueryModel model = new ProcedureProjectionQueryModel();
        model.setProcedureId(entity.getId());
        model.setEncounterId(entity.getEncounterId());
        model.setPatientId(entity.getPatientId());
        model.setCategoryEnum(entity.getCategoryEnum());
        model.setStatusEnum(entity.getStatusEnum());
        model.setOccurrenceTime(entity.getOccurrenceTime());
        return model;
    }
}
