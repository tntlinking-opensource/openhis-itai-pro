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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * 医嘱执行单读侧组装：Procedure 分页投影（B.6.1 跨聚合展示列由 AppService enrich 完成）。
 *
 * <p>本 Loader 仅搬运 {@code cln_procedure} 主表字段；项目名/操作/操作人展示列在
 * {@code OutpatientNurseStationTreatmentAppService#enrichExecutionSheetDrawerDisplay} 批量回填，
 * 禁止经 content_json / performer_list_json 中转（B.6.2）。</p>
 */
@Component
@RequiredArgsConstructor
public class OrderExecutionSheetLoader {

    private final ProcedureQueryService procedureQueryService;

    /**
     * 分页查询医嘱执行单（替代领域层 {@code OrderExecutionSheetQuerySupport}）。
     *
     * @param query 分页与过滤入参
     * @return 执行单查询模型分页（仅 Procedure 主表字段）
     */
    public Page<OrderExecutionSheetQueryModel> pageOrderExecutionSheet(OrderExecutionSheetPageQuery query) {
        Page<Procedure> page = this.procedureQueryService.pageProceduresForOrderExecutionSheet(query);
        return page.map(this::toQueryModel);
    }

    /**
     * Procedure 实体 → 执行单 QueryModel（不含 JSON 快照列）。
     *
     * @param entity 执行记录实体
     * @return 查询行模型
     */
    private OrderExecutionSheetQueryModel toQueryModel(Procedure entity) {
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
        return model;
    }
}
