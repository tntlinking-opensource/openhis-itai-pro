/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 进销存上报常量。 */
public final class ChsInventoryConstants {

    public static final String SOURCE_TYPE_DISPENSE = "DISPENSE";
    public static final String SOURCE_TYPE_INBOUND = "INBOUND";
    public static final String SOURCE_TYPE_STOCKTAKE = "STOCKTAKE";
    public static final String SOURCE_TYPE_TRANSFER = "TRANSFER";
    public static final String SOURCE_TYPE_GAIN_LOSS = "GAIN_LOSS";
    public static final String SOURCE_TYPE_MANUAL = "MANUAL";
    public static final String SOURCE_TYPE_INIT = "INIT";

    /** 差异比对：仅本地存在。 */
    public static final String COMPARE_DIFF_LOCAL_ONLY = "LOCAL_ONLY";
    /** 差异比对：仅平台存在。 */
    public static final String COMPARE_DIFF_PLATFORM_ONLY = "PLATFORM_ONLY";
    /** 差异比对：双方均存在。 */
    public static final String COMPARE_DIFF_MATCH = "MATCH";

    public static final String UPLOAD_STATUS_PENDING = "PENDING";
    public static final String UPLOAD_STATUS_SUCCESS = "SUCCESS";
    public static final String UPLOAD_STATUS_FAILED = "FAILED";
    public static final String UPLOAD_STATUS_SKIPPED = "SKIPPED";
    public static final String UPLOAD_STATUS_SKIPPED_NO_YB_NO = "SKIPPED_NO_YB_NO";
    public static final String UPLOAD_STATUS_DELETED = "DELETED";

    public static final String TRACE_POLICY_NO_YB_NO = "NO_YB_NO";
    public static final String TRACE_POLICY_TRACE_REQUIRED = "TRACE_REQUIRED";
    public static final String TRACE_POLICY_SPLIT_DISPENSE = "SPLIT_DISPENSE";
    public static final String TRACE_POLICY_NO_CODE_LIBRARY = "NO_CODE_LIBRARY";
    public static final String TRACE_POLICY_POLICY_EXEMPT = "POLICY_EXEMPT";

    public static final String TRDN_FLAG_WHOLE = "0";
    public static final String TRDN_FLAG_SPLIT = "1";

    public static final String MDTRT_SETL_TYPE_INSURANCE = "1";
    public static final String MDTRT_SETL_TYPE_SELF_PAY = "2";

    public static final String HI_FEESETL_TYPE_SELF = "0";
    public static final String HI_FEESETL_TYPE_LOCAL = "1";
    public static final String HI_FEESETL_TYPE_REMOTE = "2";

    /** 医保默认证件类型：居民身份证。 */
    public static final String PSN_CERT_TYPE_ID_CARD = "01";

    /** 平台 retRslt：成功。 */
    public static final String PLATFORM_RET_RSLT_SUCCESS = "1";
    /** 平台 retRslt：失败（本地占位）。 */
    public static final String PLATFORM_RET_RSLT_FAILED = "0";

    /** 3503 非赠品入库。 */
    public static final String PROD_GEAY_FLAG_NO = "0";

    /** 3507 inv_data_type：盘存。 */
    public static final String INV_DATA_TYPE_STOCKTAKE = "1";
    /** 3507 inv_data_type：库存变更。 */
    public static final String INV_DATA_TYPE_INV_CHANGE = "2";
    /** 3507 inv_data_type：采购。 */
    public static final String INV_DATA_TYPE_PURCHASE = "3";
    /** 3507 inv_data_type：销售。 */
    public static final String INV_DATA_TYPE_SALE = "4";

    /** 3502 inv_chg_type：调拨入库。 */
    public static final String INV_CHG_TYPE_TRANSFER_IN = "101";
    /** 3502 inv_chg_type：调拨出库。 */
    public static final String INV_CHG_TYPE_TRANSFER_OUT = "102";
    /** 3502 inv_chg_type：盘盈。 */
    public static final String INV_CHG_TYPE_STOCKTAKE_PROFIT = "103";
    /** 3502 inv_chg_type：盘损。 */
    public static final String INV_CHG_TYPE_STOCKTAKE_LOSS = "104";
    /** 3502 inv_chg_type：销毁。 */
    public static final String INV_CHG_TYPE_DESTROY = "105";
    /** 3502 inv_chg_type：其他入库（首次初始化配对 3502）。 */
    public static final String INV_CHG_TYPE_OTHER_IN = "106";
    /** 3502 inv_chg_type：其他出库（发药伴生 3502 双报，见 sale-also-inv-change3502）。 */
    public static final String INV_CHG_TYPE_OTHER_OUT = "107";
    /** 3502 inv_chg_type：商品采购入库（非默认，见 §2.3 方案 A）。 */
    public static final String INV_CHG_TYPE_PURCHASE_IN = "108";
    /** 3502 inv_chg_type：商品退货出库（非默认）。 */
    public static final String INV_CHG_TYPE_PURCHASE_RETURN_OUT = "109";
    /** 3502 inv_chg_type：赠药入库。 */
    public static final String INV_CHG_TYPE_GIFT_IN = "110";
    /** 3502 inv_chg_type：赠药退回出库。 */
    public static final String INV_CHG_TYPE_GIFT_RETURN_OUT = "111";

    /** 批初始化默认单次上限。 */
    public static final int INITIALIZE_INVENTORY_DEFAULT_MAX_LINES = 200;
    public static final int LOCAL_UPLOAD_LIST_DEFAULT_LIMIT = 500;

    /** 重试 Worker 可捞取的上传状态（失败、待上报）。 */
    public static final List<String> RETRYABLE_UPLOAD_STATUSES = Collections.unmodifiableList(Arrays.asList(
        UPLOAD_STATUS_FAILED,
        UPLOAD_STATUS_PENDING
    ));

    private ChsInventoryConstants() {
    }
}
