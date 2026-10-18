/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.tracenumber;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 追溯主档已初始化事件（DM-CUS-010 §5.1）。
 *
 * <p>触发时机：进货首次建主档时，与 {@link TraceNumberRecordRecordedEvent} 一并发布。</p>
 */
@Getter
@Setter
public class TraceNumberInitializedEvent extends DomainEvent {

    /** 追溯码字符串。 */
    private String traceNo;

    /** 追溯码所贴包装单位。 */
    private String traceUnitCode;

    /**
     * 无参构造（反序列化用）。
     */
    public TraceNumberInitializedEvent() {
        super();
    }

    /**
     * 全参构造。
     *
     * @param traceNumberId  主档标识
     * @param traceNo        追溯码
     * @param traceUnitCode  追溯码单位
     */
    public TraceNumberInitializedEvent(Long traceNumberId, String traceNo, String traceUnitCode) {
        super(traceNumberId, AggregateTypeConstants.TRACE_NUMBER);
        this.traceNo = traceNo;
        this.traceUnitCode = traceUnitCode;
    }
}
