/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.constants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 医保进销存（3501～3507）模块的<b>本系统内部</b>常量。
 *
 * <p>下列内容<b>不在</b>本类维护，请到对应位置查：</p>
 * <ul>
 *   <li>平台字段字典（{@code inv_chg_type}、{@code trdn_flag} 等）→ {@code share.enums}</li>
 *   <li>交易编号（{@code 3501}、{@code 3502}）→ {@link ChsInfNo}</li>
 * </ul>
 *
 * <p>本类常量写入 {@code yb_inv_upload} 等本地表，用于追溯「哪条 HIS 业务触发了哪次上报」。</p>
 */
public final class ChsInventoryConstants {

    /** 本地上报来源：门诊/住院发药核发（对应 3505 销售，不再单独 3502） */
    public static final String SOURCE_TYPE_DISPENSE = "DISPENSE";
    /** 本地上报来源：采购入库同意（对应 3503） */
    public static final String SOURCE_TYPE_INBOUND = "INBOUND";
    /** 本地上报来源：盘点同意产生的盈亏（对应 3502 盘盈/盘损） */
    public static final String SOURCE_TYPE_STOCKTAKE = "STOCKTAKE";
    /** 本地上报来源：库房间调拨（对应 3502 调拨入/出库） */
    public static final String SOURCE_TYPE_TRANSFER = "TRANSFER";
    /** 本地上报来源：报损报溢单（复用盘点 3502 逻辑，source 区分业务） */
    public static final String SOURCE_TYPE_GAIN_LOSS = "GAIN_LOSS";
    /** 本地上报来源：科室领用（对应 3502 其他出库 107） */
    public static final String SOURCE_TYPE_DEPT_ISSUE = "DEPT_ISSUE";
    /** 本地上报来源：科室退领（对应 3502 其他入库 106） */
    public static final String SOURCE_TYPE_DEPT_ISSUE_RETURN = "DEPT_ISSUE_RETURN";
    /** 本地上报来源：运维手工补传 */
    public static final String SOURCE_TYPE_MANUAL = "MANUAL";
    /** 本地上报来源：机构首次进销存初始化（3501+3502） */
    public static final String SOURCE_TYPE_INIT = "INIT";

    /** 本地与平台库存比对：仅本系统有记录、平台无 */
    public static final String COMPARE_DIFF_LOCAL_ONLY = "LOCAL_ONLY";
    /** 本地与平台库存比对：仅平台有记录、本地无 */
    public static final String COMPARE_DIFF_PLATFORM_ONLY = "PLATFORM_ONLY";
    /** 本地与平台库存比对：双方均有且关键字段一致 */
    public static final String COMPARE_DIFF_MATCH = "MATCH";

    /** 本地上报状态：已落库待调用 350x */
    public static final String UPLOAD_STATUS_PENDING = "PENDING";
    /** 本地上报状态：平台返回成功且本地已确认 */
    public static final String UPLOAD_STATUS_SUCCESS = "SUCCESS";
    /** 本地上报状态：平台返回失败或本地校验未通过 */
    public static final String UPLOAD_STATUS_FAILED = "FAILED";
    /** 本地上报状态：业务主动跳过（如开关关闭） */
    public static final String UPLOAD_STATUS_SKIPPED = "SKIPPED";
    /** 本地上报状态：品种未对码到医保目录，按策略跳过 */
    public static final String UPLOAD_STATUS_SKIPPED_NO_YB_NO = "SKIPPED_NO_YB_NO";
    /** 本地上报状态：已通过 3507 在平台侧删除 */
    public static final String UPLOAD_STATUS_DELETED = "DELETED";

    /** 追溯码策略：无医保目录编码，整条明细不上报 */
    public static final String TRACE_POLICY_NO_YB_NO = "NO_YB_NO";
    /** 追溯码策略：国家要求必须带追溯码，缺码则拦截或记失败 */
    public static final String TRACE_POLICY_TRACE_REQUIRED = "TRACE_REQUIRED";
    /** 追溯码策略：拆零发药，按拆零规则采集追溯码 */
    public static final String TRACE_POLICY_SPLIT_DISPENSE = "SPLIT_DISPENSE";
    /** 追溯码策略：无码库品种，按政策豁免 */
    public static final String TRACE_POLICY_NO_CODE_LIBRARY = "NO_CODE_LIBRARY";
    /** 追溯码策略：地方政策明确豁免追溯码 */
    public static final String TRACE_POLICY_POLICY_EXEMPT = "POLICY_EXEMPT";

    /** 350x 部分查询接口返回的 {@code retRslt}：成功 */
    public static final String PLATFORM_RET_RSLT_SUCCESS = "1";
    /** 350x 部分查询接口返回的 {@code retRslt}：失败（或本地占位失败） */
    public static final String PLATFORM_RET_RSLT_FAILED = "0";

    /** 机构进销存初始化：单次请求最大明细行数（防止超时） */
    public static final int INITIALIZE_INVENTORY_DEFAULT_MAX_LINES = 200;
    /** 本地上报记录列表查询默认上限 */
    public static final int LOCAL_UPLOAD_LIST_DEFAULT_LIMIT = 500;

    /** 重试任务可捞取的本地状态：失败 + 待上报 */
    public static final List<String> RETRYABLE_UPLOAD_STATUSES = Collections.unmodifiableList(Arrays.asList(
        UPLOAD_STATUS_FAILED,
        UPLOAD_STATUS_PENDING
    ));

    private ChsInventoryConstants() {
    }
}
