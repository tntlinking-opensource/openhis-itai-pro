/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.gateway.scheme.push.assembler;

import com.openhis.whale.crb.gateway.scheme.push.model.PushAssemblyContext;
import com.openhis.whale.crb.gateway.scheme.push.model.PushOperationMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 推送操作元数据组装器（P10-C/D）。
 *
 * <p><b>职责：</b>将机构/操作人/操作时间组装为 {@link PushOperationMeta}，统一 operationTime 格式化规则。</p>
 * <p><b>实现位置：</b>本类（gateway {@code scheme.push.assembler}）</p>
 * <p><b>调用方：</b>bridge {@code EmrPatientInfoMapper} 及各 resource payload assembler</p>
 * <p><b>架构边界：</b>gateway 本地静态工具，不依赖 bridge {@code CrbMappingContext}。</p>
 * <p><b>迁移阶段：</b>P10-C/D</p>
 */
public final class PushOperationMetaAssembler {

    private static final DateTimeFormatter OPERATION_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private PushOperationMetaAssembler() {
    }

    /**
     * 按离散字段组装操作元数据。
     *
     * @param orgCode 国家机构代码
     * @param orgName 机构名称
     * @param operatorId 操作人 id
     * @param operationTime 操作时刻；null 时不设置 operationTime
     * @return 已填充的 {@link PushOperationMeta}
     */
    public static PushOperationMeta assemble(
            String orgCode,
            String orgName,
            String operatorId,
            Instant operationTime) {
        PushOperationMeta meta = new PushOperationMeta();
        if (orgCode != null) {
            meta.setOrgCode(orgCode);
        }
        if (orgName != null) {
            meta.setOrgName(orgName);
        }
        if (operatorId != null) {
            meta.setOperatorId(operatorId);
        }
        if (operationTime != null) {
            meta.setOperationTime(OPERATION_TIME_FMT.format(operationTime));
        }
        return meta;
    }

    /**
     * 从 {@link PushAssemblyContext} 组装操作元数据。
     *
     * @param ctx gateway 组装上下文；null 时等价于全 null 字段
     * @param operationTime 操作时刻
     * @return 已填充的 {@link PushOperationMeta}
     */
    public static PushOperationMeta assemble(PushAssemblyContext ctx, Instant operationTime) {
        if (ctx == null) {
            return assemble(null, null, null, operationTime);
        }
        return assemble(ctx.orgCode(), ctx.orgName(), ctx.operatorId(), operationTime);
    }
}
