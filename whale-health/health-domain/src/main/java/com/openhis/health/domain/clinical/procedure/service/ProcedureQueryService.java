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
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;

import jakarta.persistence.criteria.Predicate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 诊疗执行（Procedure）记录读侧查询服务。
 *
 * <p>职责：按来源请求加载执行记录、分页投影、详情（含执行人轨迹）及可完成性校验。</p>
 *
 * <p>边界：只读；部分方法对缺失主键返回 {@code null} 或失败原因模型，而非统一抛异常。</p>
 */
@Service
@RequiredArgsConstructor
public class ProcedureQueryService {
    private final ProcedureRepository procedureRepository;
    private final ProcedurePerformerRepository performerRepository;

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
        if (requestId != null && StringUtils.isNotBlank(requestTable)) {
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
        if (StringUtils.isBlank(requestTable)) {
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
