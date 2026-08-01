/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.audit.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 3103 warns 元素 */
@Data
@Accessors(chain = true)
public class AuditFeedbackWarnCommand {

    /**
     * 提醒结果 ID。
     * <p>吉林落地填违规明细 {@code jrd_id}，不是违规头 {@code jr_id}。</p>
     */
    private String warnRsltId;
    /** 处置方式：1 继续 / 2 返回修改 */
    private String dspoWay;
    /** 处置原因（dspoWay=1 时必填） */
    private String dspoWayRea;
    /** 关联违规头 jrId（落库冗余，可选） */
    private String jrId;
}
