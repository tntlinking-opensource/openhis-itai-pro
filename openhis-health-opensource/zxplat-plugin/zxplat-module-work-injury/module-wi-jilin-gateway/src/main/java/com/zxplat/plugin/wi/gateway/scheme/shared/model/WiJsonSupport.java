/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.gateway.scheme.shared.model;

import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsSchemeJsonSupport;

import java.util.Map;

/**
 * 工伤 DTO ↔ 平台 JSON Map 转换（prepareInvoke 业务体）。
 */
public final class WiJsonSupport {

    private WiJsonSupport() {
    }

    /**
     * 将 DTO 转为平台 input 对象（保留 @JSONField 蛇形命名）。
     *
     * @param dto 业务 DTO
     * @return 可 JSON 序列化的 Map
     */
    public static Map<String, Object> toInputMap(Object dto) {
        return ChsSchemeJsonSupport.toInputMap(dto);
    }
}
