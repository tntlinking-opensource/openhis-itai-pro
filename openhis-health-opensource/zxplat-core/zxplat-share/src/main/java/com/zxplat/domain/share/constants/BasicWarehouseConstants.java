/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 基础资料「库房药房」维护页应用层字面量。
 *
 * <p>禁止在 {@code WarehouseAppService} / {@code WarehouseMapperImpl} 内重复声明。</p>
 */
public interface BasicWarehouseConstants {

    /**
     * 库房维护页应用层停用原因（写入领域 {@code DeactivateLocationCommand}）。
     */
    String REASON_DEACTIVATE_BY_APP = "应用层停用库房";

    /**
     * 库房/药房点位登记时默认 {@code type_json} 空对象快照。
     */
    String EMPTY_LOCATION_TYPE_JSON = "{}";
}
