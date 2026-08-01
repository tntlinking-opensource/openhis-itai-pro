/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.share;

/**
 * Path B（yunlis / MedSync）相关常量。
 */
public final class CrbLabPathBConstants {

    private CrbLabPathBConstants() {
    }

    /** APPLYNO 命中方式：按 ServiceRequest.prescriptionNo。 */
    public static final String MATCHED_BY_PRESCRIPTION_NO = "PRESCRIPTION_NO";

    /** APPLYNO 命中方式：按 ServiceRequest.id。 */
    public static final String MATCHED_BY_SERVICE_REQUEST_ID = "SERVICE_REQUEST_ID";

    /** 体检单 TYPE，永不入队。 */
    public static final String LAB_TYPE_PHYSICAL_EXAM = "04";

    /** 可消费审核态。 */
    public static final String RECEIVE_FLAG_AUDITED = "6";

    /** 可消费已打印态。 */
    public static final String RECEIVE_FLAG_PRINTED = "7";

    /** Outbox 待处理。 */
    public static final String OUTBOX_PENDING = "PENDING";

    /** Outbox 已入队成功。 */
    public static final String OUTBOX_DONE = "DONE";

    /** Outbox 业务跳过（体检/无白名单等）。 */
    public static final String OUTBOX_SKIP = "SKIP";

    /** Outbox 处理失败可重试。 */
    public static final String OUTBOX_ERROR = "ERROR";

    /** 单次轮询最大条数。 */
    public static final int OUTBOX_BATCH_SIZE = 50;

    /** receive resource：检验报告头。 */
    public static final String RESOURCE_EMR_EX_LAB = "emrExLab";

    /** receive resource：检验明细。 */
    public static final String RESOURCE_EMR_EX_LAB_ITEM = "emrExLabItem";

    /** lab.source：外部 LIS（外部 yunlis）。 */
    public static final String LAB_SOURCE_EXTERNAL = "external";

    /** 门诊 TYPE。 */
    public static final String LAB_TYPE_OUTPATIENT = "01";

    /** 住院 TYPE。 */
    public static final String LAB_TYPE_INPATIENT = "02";

    /** 国标活动类型：门诊。 */
    public static final String ACTIVITY_TYPE_OUTPATIENT_CODE = "1";

    /** 国标活动类型名称：门诊。 */
    public static final String ACTIVITY_TYPE_OUTPATIENT_NAME = "门诊";

    /** 国标活动类型：住院。 */
    public static final String ACTIVITY_TYPE_INPATIENT_CODE = "2";

    /** 国标活动类型名称：住院。 */
    public static final String ACTIVITY_TYPE_INPATIENT_NAME = "住院";

    /** Path B 默认操作人（无审核人映射时）。 */
    public static final String DEFAULT_OPERATOR_ID = "CRB_SYSTEM";

    /** Outbox process_message 最大长度。 */
    public static final int OUTBOX_MESSAGE_MAX_LENGTH = 500;
}
