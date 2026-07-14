/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.gateway.scheme.push.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * receive POST 公共操作元数据（P10-C/D）。
 *
 * <p><b>职责：</b>封装各 EMR receive 报文共用的机构与操作人字段，与平台 JSON 顶层 key 一一对应。</p>
 * <p><b>实现位置：</b>本类；由 {@code PushOperationMetaAssembler} 组装</p>
 * <p><b>调用方：</b>{@code EmrPatientInfoPayloadAssembler} 及后续各 resource payload assembler</p>
 * <p><b>架构边界：</b>gateway scheme model，bridge 通过 assembler 引用，禁止在 bridge 硬编码 orgCode 等 key。</p>
 * <p><b>迁移阶段：</b>P10-C/D</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class PushOperationMeta {

    /** 国家机构代码（JSON key: {@code orgCode}）。 */
    private String orgCode;

    /** 机构名称（JSON key: {@code orgName}）。 */
    private String orgName;

    /** 操作人 id（JSON key: {@code operatorId}）。 */
    private String operatorId;

    /** 操作时间，格式 {@code yyyy-MM-dd HH:mm:ss}（JSON key: {@code operationTime}）。 */
    private String operationTime;
}
