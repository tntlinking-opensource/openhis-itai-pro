/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.gateway.scheme.push.model;

/**
 * gateway 侧推送组装上下文（P10-C/D）。
 *
 * <p><b>职责：</b>在 gateway scheme 层传递机构与操作人信息，替代 bridge {@code CrbMappingContext} 的直接依赖。</p>
 * <p><b>实现位置：</b>本 record</p>
 * <p><b>调用方：</b>{@code PushOperationMetaAssembler}、bridge {@code EmrPatientInfoMapper}</p>
 * <p><b>架构边界：</b>gateway 本地 DTO；bridge mapper 从 {@code CrbMappingContext} 映射为本 record 后交给 assembler。</p>
 * <p><b>迁移阶段：</b>P10-C/D</p>
 *
 * @param orgCode 国家机构代码（JSON key: {@code orgCode}）
 * @param orgName 机构名称（JSON key: {@code orgName}）
 * @param operatorId 操作人 id（JSON key: {@code operatorId}）
 */
public record PushAssemblyContext(String orgCode, String orgName, String operatorId) {
}
