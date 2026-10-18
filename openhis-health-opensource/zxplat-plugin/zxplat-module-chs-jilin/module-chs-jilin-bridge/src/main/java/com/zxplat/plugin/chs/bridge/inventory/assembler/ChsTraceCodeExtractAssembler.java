/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.assembler;

import com.zxplat.domain.custom.tracenumber.queries.model.TraceNumberRecordQueryModel;
import com.zxplat.domain.share.constants.GeneralSharedConstants;

import java.util.ArrayList;
import java.util.List;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * CHS 进销存上报追溯码提取静态辅助（无 IO、无 Spring 依赖）。
 *
 * <p>读取 {@link com.zxplat.domain.custom.tracenumber} 子表流水，
 * 优先使用发放行 {@code traceNo} 逗号串；为空时回落子表流水过滤结果（B.6.6 只读装配）。</p>
 */
public final class ChsTraceCodeExtractAssembler {

    /**
     * 禁止实例化 */
    private ChsTraceCodeExtractAssembler() {
    }

    /**
     * 提取 OUT 方向追溯码（3505 销售）。
     *
     * @param traceNoFromDispense 发放行逗号串（可空）
     * @param flowRecords         子表流水读模型
     * @param flowDirectionOut    OUT 方向 code
     * @param busNo               业务单号（可空则不过滤）
     * @return 追溯码列表 */
    public static List<String> extractOutboundCodes(
        String traceNoFromDispense,
        List<TraceNumberRecordQueryModel> flowRecords,
        String flowDirectionOut,
        String busNo
    ) {
        List<String> fromDispense = splitTraceNoCommaString(traceNoFromDispense);
        if (!fromDispense.isEmpty()) {
            return fromDispense;
        }
        return filterFlowRecordTraceNos(flowRecords, flowDirectionOut, null, busNo);
    }

    /**
     * 提取退药 IN 方向追溯码（3506 销售退货）。
     *
     * @param traceNoFromDispense 发放行逗号串
     * @param flowRecords         子表流水
     * @param flowDirectionIn     IN 方向 code
     * @param returnOperationType 退药操作类型 code
     * @param busNo               业务单号
     * @return 追溯码列表 */
    public static List<String> extractReturnInCodes(
        String traceNoFromDispense,
        List<TraceNumberRecordQueryModel> flowRecords,
        String flowDirectionIn,
        String returnOperationType,
        String busNo
    ) {
        List<String> fromDispense = splitTraceNoCommaString(traceNoFromDispense);
        if (!fromDispense.isEmpty()) {
            return fromDispense;
        }
        return filterFlowRecordTraceNos(flowRecords, flowDirectionIn, returnOperationType, busNo);
    }

    /**
     * 逗号串拆码。
     *
     * @param traceNoCommaString 逗号串
     * @return 非空码列表 */
    public static List<String> splitTraceNoCommaString(String traceNoCommaString) {
        List<String> codes = new ArrayList<>();
        if (isEmpty(traceNoCommaString)) {
            return codes;
        }
        String[] parts = traceNoCommaString.split(GeneralSharedConstants.COMMA);
        for (String part : parts) {
            String normalized = trimToNull(part);
            if (normalized != null) {
                codes.add(normalized);
            }
        }
        return codes;
    }

    /**
     * 按方向/操作类型/单号过滤子表流水追溯码。
     *
     * @param flowRecords   流水读模型
     * @param flowDirection 轨迹方向（可空则不过滤）
     * @param operationType 操作类型（可空则不过滤）
     * @param busNo         业务单号（可空则不过滤）
     * @return 追溯码列表 */
    private static List<String> filterFlowRecordTraceNos(
        List<TraceNumberRecordQueryModel> flowRecords,
        String flowDirection,
        String operationType,
        String busNo
    ) {
        List<String> codes = new ArrayList<>();
        if (flowRecords == null || flowRecords.isEmpty()) {
            return codes;
        }
        for (TraceNumberRecordQueryModel row : flowRecords) {
            if (row == null || isEmpty(row.getTraceNo())) {
                continue;
            }
            if (isNotEmpty(flowDirection)
                && !flowDirection.equals(row.getRecordDirectionEnum())) {
                continue;
            }
            if (isNotEmpty(operationType)
                && !operationType.equals(row.getOperationType())) {
                continue;
            }
            if (isNotEmpty(busNo) && !busNo.equals(row.getBusNo())) {
                continue;
            }
            codes.add(row.getTraceNo().trim());
        }
        return codes;
    }
}
