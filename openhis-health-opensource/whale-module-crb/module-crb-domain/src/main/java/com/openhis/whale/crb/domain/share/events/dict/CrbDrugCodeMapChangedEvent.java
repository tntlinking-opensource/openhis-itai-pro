/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.share.events.dict;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 药品对照变更事件（最小载荷：mapId + medBusNo）。
 */
@Getter
@AllArgsConstructor
public class CrbDrugCodeMapChangedEvent {

    /** 对照主键。 */
    private final Long mapId;

    /** 院内药品业务码。 */
    private final String medBusNo;
}
