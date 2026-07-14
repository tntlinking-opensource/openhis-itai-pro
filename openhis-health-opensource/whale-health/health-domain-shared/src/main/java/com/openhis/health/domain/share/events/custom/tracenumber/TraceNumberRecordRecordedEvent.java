/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.tracenumber;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 追溯记录已写入事件（DM-CUS-010 §5.1）。
 *
 * <p>触发时机：{@code recordTraceRecords} 落库成功后 beforeCommit 发布。</p>
 */
@Getter
@Setter
public class TraceNumberRecordRecordedEvent extends DomainEvent {

    /** 追溯码主档标识。 */
    private Long traceNumberId;

    /** 追溯码字符串。 */
    private String traceNo;

    /** 本次写入记录条数。 */
    private Integer recordCount;

    /** 业务单据号。 */
    private String busNo;

    /** 操作类型 code。 */
    private String operationType;

    /**
     * 无参构造（反序列化用）。
     */
    public TraceNumberRecordRecordedEvent() {
        super();
    }

    /**
     * 全参构造。
     *
     * @param traceNumberId 主档标识
     * @param traceNo       追溯码
     * @param recordCount   记录条数
     * @param busNo         业务单号
     * @param operationType 操作类型
     */
    public TraceNumberRecordRecordedEvent(
        Long traceNumberId,
        String traceNo,
        Integer recordCount,
        String busNo,
        String operationType
    ) {
        super(traceNumberId, AggregateTypeConstants.TRACE_NUMBER);
        this.traceNumberId = traceNumberId;
        this.traceNo = traceNo;
        this.recordCount = recordCount;
        this.busNo = busNo;
        this.operationType = operationType;
    }
}
