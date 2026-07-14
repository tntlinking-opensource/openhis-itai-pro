/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.tracenumber;

import com.openhis.health.domain.custom.tracenumber.queries.model.TraceNumberIdentityQueryModel;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TraceNumberConstants;
import com.openhis.health.domain.share.enums.TraceIdentityResolveStatus;
import com.openhis.whale.core.application.exception.BusinessException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * IF-TRC-21 批量扫码身份反查只读 Support（AS-CUS-TRC-001 · BR-TRC-013～015）。
 *
 * <p>无 Spring、无 I/O；供各业务 AppService 在调用 {@code TraceNumberQueryService} 前后做 Gate 与结果拆分。</p>
 */
public final class TraceNumberBatchIdentityResolver {

    /**
     * 禁止实例化。
     */
    private TraceNumberBatchIdentityResolver() {
    }

    /**
     * Gate：批量码非空且不超过上限。
     *
     * @param traceNos 追溯码列表
     * @throws BusinessException 空列表或超限时
     */
    public static void assertBatchSizeWithinLimit(List<String> traceNos) {
        if (traceNos == null || traceNos.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "追溯码列表不能为空");
        }
        if (traceNos.size() > TraceNumberConstants.BATCH_RESOLVE_MAX_CODES) {
            throw new BusinessException(
                ErrorCodeConstants.TRACE_NUMBER_BATCH_LIMIT_EXCEEDED,
                "单次最多解析" + TraceNumberConstants.BATCH_RESOLVE_MAX_CODES + "个追溯码");
        }
    }

    /**
     * 规范化入参码列表（trim，保留空位占位）。
     *
     * @param traceNos 原始列表
     * @return 规范化列表
     */
    public static List<String> normalizeTraceNos(List<String> traceNos) {
        List<String> result = new ArrayList<>();
        if (traceNos == null) {
            return result;
        }
        for (String traceNo : traceNos) {
            String normalized = trimToNull(traceNo);
            if (normalized == null) {
                result.add("");
            } else {
                result.add(normalized);
            }
        }
        return result;
    }

    /**
     * 将领域批量解析结果拆为成功项与失败项。
     *
     * @param models 领域读模型（与入参同序）
     * @return 拆分结果
     */
    public static ResolvedBatch splitResolvedItems(List<TraceNumberIdentityQueryModel> models) {
        ResolvedBatch batch = new ResolvedBatch();
        batch.setItems(new ArrayList<>());
        batch.setFailItems(new ArrayList<>());
        if (models == null) {
            return batch;
        }
        for (TraceNumberIdentityQueryModel model : models) {
            if (model == null) {
                continue;
            }
            String status = model.getResolveStatusEnum();
            if (TraceIdentityResolveStatus.OK.getCode().equals(status)) {
                batch.getItems().add(model);
            } else {
                FailItem failItem = new FailItem();
                failItem.setTraceNo(model.getTraceNo());
                failItem.setResolveStatusEnum(status);
                batch.getFailItems().add(failItem);
            }
        }
        return batch;
    }

    /**
     * 请求内重复码检测（Gate 辅助，领域亦会标记 DUPLICATE_IN_REQUEST）。
     *
     * @param traceNos 追溯码列表
     * @throws BusinessException 请求内重复时
     */
    public static void assertNoDuplicateInRequest(List<String> traceNos) {
        if (traceNos == null || traceNos.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (String traceNo : traceNos) {
            String normalized = trimToNull(traceNo);
            if (normalized == null) {
                continue;
            }
            if (seen.contains(normalized)) {
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_DUPLICATE_IN_REQUEST,
                    "同一请求内追溯码重复");
            }
            seen.add(normalized);
        }
    }

    /**
     * 批量解析拆分结果。
     */
    public static final class ResolvedBatch {

        /** 成功解析项。 */
        private List<TraceNumberIdentityQueryModel> items;

        /** 失败项。 */
        private List<FailItem> failItems;

        /**
         * @return 成功项
         */
        public List<TraceNumberIdentityQueryModel> getItems() {
            return items;
        }

        /**
         * @param items 成功项
         */
        public void setItems(List<TraceNumberIdentityQueryModel> items) {
            this.items = items;
        }

        /**
         * @return 失败项
         */
        public List<FailItem> getFailItems() {
            return failItems;
        }

        /**
         * @param failItems 失败项
         */
        public void setFailItems(List<FailItem> failItems) {
            this.failItems = failItems;
        }
    }

    /**
     * 单码解析失败项。
     */
    public static final class FailItem {

        /** 追溯码。 */
        private String traceNo;

        /** 解析状态 code。 */
        private String resolveStatusEnum;

        /**
         * @return 追溯码
         */
        public String getTraceNo() {
            return traceNo;
        }

        /**
         * @param traceNo 追溯码
         */
        public void setTraceNo(String traceNo) {
            this.traceNo = traceNo;
        }

        /**
         * @return 解析状态
         */
        public String getResolveStatusEnum() {
            return resolveStatusEnum;
        }

        /**
         * @param resolveStatusEnum 解析状态
         */
        public void setResolveStatusEnum(String resolveStatusEnum) {
            this.resolveStatusEnum = resolveStatusEnum;
        }
    }
}
