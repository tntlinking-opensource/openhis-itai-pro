/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 追溯码聚合（DM-CUS-010）业务常量。
 *
 * <p>包路径：{@code com.openhis.health.domain.custom.tracenumber}；本期仅药品追溯。</p>
 */
public interface TraceNumberConstants {

    /**
     * 追溯码报表查询范围：全院（不展示本库列）。
     */
    String REPORT_QUERY_SCOPE_HOSPITAL = "HOSPITAL";

    /**
     * 追溯码报表查询范围：指定库房（须 locationId，展示本库状态/净量）。
     */
    String REPORT_QUERY_SCOPE_LOCATION = "LOCATION";

    /**
     * 子表物理表名（TraceNumberRecord）。
     */
    String TABLE_CUS_TRACE_NUMBER_RECORD = "cus_trace_number_record";

/** TraceNumber 共享常量：cus trace number，值 {@code cus_trace_number}。 */
    String TABLE_CUS_TRACE_NUMBER = "cus_trace_number";

    /**
     * 批量扫码身份反查单次上限（AS-CUS-TRC-001 · BR-TRC-015）。
     */
    int BATCH_RESOLVE_MAX_CODES = 50;

    /**
     * 批量扫码场景上下文：发药/出库（NOT_IN_HOSPITAL 记失败项）。
     */
    String BATCH_RESOLVE_SCENE_DISPENSE = "DISPENSE";

    /**
     * 批量扫码场景上下文：进货（NOT_FOUND 可接受）。
     */
    String BATCH_RESOLVE_SCENE_INBOUND = "INBOUND";

    /**
     * 批量扫码场景上下文：护士分配。
     */
    String BATCH_RESOLVE_SCENE_NURSE_ASSIGN = "NURSE_ASSIGN";

    /**
     * 批量扫码场景上下文：退药。
     */
    String BATCH_RESOLVE_SCENE_RETURN = "RETURN";
}
