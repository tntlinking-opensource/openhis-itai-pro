/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.share.events.dict;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 检验项目对照变更事件（最小载荷：mapId + itemBusNo）。
 */
@Getter
@AllArgsConstructor
public class CrbLabItemMapChangedEvent {

    /** 对照主键。 */
    private final Long mapId;

    /** 院内项目业务码。 */
    private final String itemBusNo;
}
