/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.audit.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 3103 warns 元素 */
@Data
@Accessors(chain = true)
public class Audit3103WarnRow {

    /** 提醒结果 ID（吉林填 jrd_id） */
    @JSONField(name = "warn_rslt_id")
    private String warnRsltId;

    @JSONField(name = "dspo_way")
    private String dspoWay;

    @JSONField(name = "dspo_way_rea")
    private String dspoWayRea;
}
