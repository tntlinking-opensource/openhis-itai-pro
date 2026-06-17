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

package com.openhis.health.module.querysupport.procedure;

import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.queries.OrderExecutionSheetPageQuery;
import com.openhis.health.domain.clinical.procedure.queries.model.OrderExecutionSheetQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 医嘱执行单读侧组装：Procedure 分页 + 服务/药品请求 content_json + 执行人列表（B.6.1 跨聚合在应用层）。
 *
 * <p>对外 Response 须 Mapper 解析 JSON 字段，禁止原样透出（B.6.2）。</p>
 */
@Component
@RequiredArgsConstructor
public class OrderExecutionSheetLoader {

    private final ProcedureQueryService procedureQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final MedicationRequestQueryService medicationRequestQueryService;

    /**
     * 分页查询医嘱执行单（替代领域层 {@code OrderExecutionSheetQuerySupport}）。
     *
     * @param query 分页与过滤入参
     * @return 富化后的执行单查询模型分页
     */
    public Page<OrderExecutionSheetQueryModel> pageOrderExecutionSheet(OrderExecutionSheetPageQuery query) {
        Page<Procedure> page = this.procedureQueryService.pageProceduresForOrderExecutionSheet(query);
        EnrichmentContext ctx = this.buildEnrichmentContext(page.getContent());
        return page.map(row -> this.toQueryModel(row, ctx));
    }

    /**
     * 批量加载服务/药品 content_json 与执行人 JSON，供 QueryModel 装配（Mapper 再结构化，B.6.2）。
     */
    private EnrichmentContext buildEnrichmentContext(List<Procedure> procedures) {
        EnrichmentContext ctx = new EnrichmentContext();
        if (procedures == null || procedures.isEmpty()) {
            return ctx;
        }
        Set<Long> serviceRequestIds = new LinkedHashSet<>();
        Set<Long> medicationRequestIds = new LinkedHashSet<>();
        List<Long> procedureIds = new ArrayList<>();
        for (Procedure procedure : procedures) {
            if (procedure == null || procedure.getId() == null) {
                continue;
            }
            procedureIds.add(procedure.getId());
            if (procedure.getRequestId() == null || StringUtils.isBlank(procedure.getRequestTable())) {
                continue;
            }
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(procedure.getRequestTable())) {
                serviceRequestIds.add(procedure.getRequestId());
            } else if (TableNameConstants.MED_MEDICATION_REQUEST.equals(procedure.getRequestTable())) {
                medicationRequestIds.add(procedure.getRequestId());
            }
        }
        if (!serviceRequestIds.isEmpty()) {
            ctx.contentJsonByRequestKey.putAll(this.loadServiceRequestContentJson(serviceRequestIds));
        }
        if (!medicationRequestIds.isEmpty()) {
            Map<Long, String> medJson = this.medicationRequestQueryService.mapContentJsonByIds(medicationRequestIds);
            for (Map.Entry<Long, String> entry : medJson.entrySet()) {
                ctx.contentJsonByRequestKey.put(
                    this.requestKey(TableNameConstants.MED_MEDICATION_REQUEST, entry.getKey()),
                    entry.getValue());
            }
        }
        ctx.performerJsonByProcedureId.putAll(
            this.procedureQueryService.mapPerformerListJsonByProcedureIds(procedureIds));
        return ctx;
    }

    /**
     * 按服务申请主键批量取 content_json 快照。
     */
    private Map<String, String> loadServiceRequestContentJson(Set<Long> serviceRequestIds) {
        Map<Long, ServiceRequestListModel> rows = this.serviceRequestQueryService.mapListModelsByIds(serviceRequestIds);
        Map<String, String> map = new HashMap<>();
        for (Long id : serviceRequestIds) {
            ServiceRequestListModel row = rows.get(id);
            if (row == null || StringUtils.isBlank(row.getContentJson())) {
                continue;
            }
            map.put(this.requestKey(TableNameConstants.WKF_SERVICE_REQUEST, id), row.getContentJson());
        }
        return map;
    }

    /**
     * Procedure 行 + 富化 JSON → 执行单 QueryModel（JSON 字段仅 Loader 内部，禁止直透 HTTP）。
     */
    private OrderExecutionSheetQueryModel toQueryModel(Procedure entity, EnrichmentContext ctx) {
        OrderExecutionSheetQueryModel model = new OrderExecutionSheetQueryModel();
        model.setProcedureId(entity.getId());
        model.setRequestId(entity.getRequestId());
        model.setRequestTable(entity.getRequestTable());
        model.setStatusEnum(entity.getStatusEnum());
        model.setCategoryEnum(entity.getCategoryEnum());
        model.setPatientId(entity.getPatientId());
        model.setEncounterId(entity.getEncounterId());
        model.setOccurrenceTime(entity.getOccurrenceTime());
        model.setRecordedTime(entity.getRecordedTime());
        model.setRecorderId(entity.getRecorderId());
        model.setResultText(entity.getResultText());
        if (entity.getRequestId() != null && StringUtils.isNotBlank(entity.getRequestTable())) {
            model.setOrderContentJson(ctx.contentJsonByRequestKey.get(
                this.requestKey(entity.getRequestTable(), entity.getRequestId())));
        }
        model.setPerformerListJson(ctx.performerJsonByProcedureId.get(entity.getId()));
        return model;
    }

    /**
     * 请求表名 + 主键复合键（分隔符 SOH，避免表名字面量含 {@code #} 时碰撞）。
     *
     * @param requestTable 医嘱请求物理表名
     * @param requestId    请求主键
     * @return 富化 Map 键
     */
    private String requestKey(String requestTable, Long requestId) {
        return requestTable + '\u0001' + requestId;
    }

    /** 执行单分页行的库表 JSON 快照缓存（仅 Mapper 结构化，禁止 HTTP 直透 B.6.2）。 */
    private static final class EnrichmentContext {
        private final Map<String, String> contentJsonByRequestKey = new HashMap<>();
        private final Map<Long, String> performerJsonByProcedureId = new HashMap<>();
    }
}
