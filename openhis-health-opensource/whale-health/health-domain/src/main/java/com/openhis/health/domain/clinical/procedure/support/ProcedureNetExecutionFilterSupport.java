/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.procedure.support;

import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.EventStatus;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 执行记录净 COMPLETED 过滤（DM-CLN-004 §6.2.6；AS-RPT-002 BR-RPT-NWL-004）。
 *
 * <p>仅处理 {@code wkf_service_request} 锚点；不含 {@code med_medication_request}（BR-RPT-NWL-003）。</p>
 */
@Component
public class ProcedureNetExecutionFilterSupport {

    /**
     * 自候选集中筛出未被 CANCEL 对冲的 COMPLETED 行。
     *
     * @param candidates        同窗内加载的执行记录（含 COMPLETED / 其它态）
     * @param cancelRowsForOffset 可能指向候选 COMPLETED 的 CANCEL 行（可含窗外对冲行）
     * @return 净 COMPLETED 列表
     */
    public List<Procedure> filterNetCompletedServiceRequestProcedures(
        Collection<Procedure> candidates,
        Collection<Procedure> cancelRowsForOffset) {
        Set<Long> offsetCompletedIds = this.collectOffsetCompletedIds(cancelRowsForOffset);
        List<Procedure> netRows = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) {
            return netRows;
        }
        // 遍历：仅保留诊疗 SR 锚点且未被 refund_id 对冲的 COMPLETED 行
        for (Procedure entity : candidates) {
            if (entity == null || entity.getId() == null) {
                continue;
            }
            if (!TableNameConstants.WKF_SERVICE_REQUEST.equals(entity.getRequestTable())) {
                continue;
            }
            if (!EventStatus.COMPLETED.getCode().equals(entity.getStatusEnum())) {
                continue;
            }
            if (offsetCompletedIds.contains(entity.getId())) {
                continue;
            }
            netRows.add(entity);
        }
        return netRows;
    }

    /**
     * 收集被 CANCEL 指向的对冲 COMPLETED 主键集合。
     *
     * @param cancelRows CANCEL 行集合
     * @return 被对冲的 COMPLETED id
     */
    public Set<Long> collectOffsetCompletedIds(Collection<Procedure> cancelRows) {
        Set<Long> offsetCompletedIds = new HashSet<>();
        if (cancelRows == null || cancelRows.isEmpty()) {
            return offsetCompletedIds;
        }
        for (Procedure entity : cancelRows) {
            if (entity == null || entity.getId() == null) {
                continue;
            }
            if (!TableNameConstants.WKF_SERVICE_REQUEST.equals(entity.getRequestTable())) {
                continue;
            }
            if (!EventStatus.isCancelExecutionStatusCode(entity.getStatusEnum())) {
                continue;
            }
            if (entity.getRefundId() != null) {
                offsetCompletedIds.add(entity.getRefundId());
            }
        }
        return offsetCompletedIds;
    }

    /**
     * 判断来源表是否为诊疗服务申请。
     *
     * @param requestTable 来源表持久化名
     * @return 是否为 {@code wkf_service_request}
     */
    public boolean isServiceRequestTable(String requestTable) {
        return StringUtils.isNotEmpty(requestTable)
            && TableNameConstants.WKF_SERVICE_REQUEST.equals(requestTable);
    }
}
