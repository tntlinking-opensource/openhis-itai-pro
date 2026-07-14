/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.gateway.scheme.push.share;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * scheme payload 与 JSON 互转辅助（P10-C/D）。
 *
 * <p><b>职责：</b>将 gateway scheme model 序列化为 {@link ObjectNode} 或 JSON 字符串，供 receive POST 与 bridge mapper 复用。</p>
 * <p><b>实现位置：</b>本类（gateway {@code scheme.push.share}）</p>
 * <p><b>调用方：</b>bridge {@code EmrPatientInfoMapper}、gateway {@code FrontSoftwareResponseParser} 薄委托链</p>
 * <p><b>架构边界：</b>gateway 本地工具，不依赖 bridge；统一 Jackson 配置避免与 bridge 重复 ObjectMapper。</p>
 * <p><b>迁移阶段：</b>P10-C/D</p>
 */
public final class PushSchemeJsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PushSchemeJsonSupport() {
    }

    /**
     * 将 scheme model 转为 {@link ObjectNode}（null 安全）。
     *
     * @param payload scheme model 或 Map
     * @return 树形节点；payload 为 null 时返回 null
     */
    public static ObjectNode toObjectNode(Object payload) {
        if (payload == null) {
            return null;
        }
        return MAPPER.valueToTree(payload);
    }

    /**
     * 将 scheme model 序列化为 JSON 字符串。
     *
     * @param payload scheme model
     * @return JSON 文本
     * @throws com.fasterxml.jackson.core.JsonProcessingException 序列化失败
     */
    public static String toJson(Object payload) throws com.fasterxml.jackson.core.JsonProcessingException {
        return MAPPER.writeValueAsString(payload);
    }
}
