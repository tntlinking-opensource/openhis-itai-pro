/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.invoke.queries;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 医保网关调用日志分页条件（{@code yb_invoke_log}）。
 *
 * <p>时间窗必填；就诊号由应用层解析为 {@link #encounterId} 后再传入，本对象不包含 busNo。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class InvokeLogPageQuery {

    /** 创建时间起（含） */
    private LocalDateTime createdFrom;

    /** 创建时间止（含） */
    private LocalDateTime createdTo;

    /** 交易编号（精确，trim） */
    private String infno;

    /**
     * 成功/失败：{@code true}=infcode 为 {@code 0}；{@code false}=非成功；{@code null}=不限。
     */
    private Boolean success;

    /** 发送方报文 ID（精确） */
    private String msgid;

    /** 契约业务号（精确） */
    private String contractBusNo;

    /** 就诊主键 */
    private Long encounterId;

    /**
     * 就诊主键集合（患者姓名等拼装解析后的 IN 条件；与 {@link #encounterId} 同时存在时应用层应先收敛）。
     */
    private List<Long> encounterIds;

    /** 收费批次号（精确） */
    private String chrgBchno;

    /** 支付单/草稿 ID */
    private Long paymentDraftId;

    /** 结算回执 ID */
    private Long settlementRecordId;

    /** 错误信息关键字（模糊） */
    private String errMsgKeyword;

    /** 页码（从 1 起） */
    private Integer pageNo;

    /** 每页条数 */
    private Integer pageSize;
}
