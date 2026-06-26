/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.support;

/**
 * MRMS 模块业务错误码常量。
 */
public final class MrmsErrorCodes {

    private MrmsErrorCodes() {
    }

    /** 病案主档不存在。 */
    public static final String CASE_RECORD_NOT_FOUND = "MRMS_CASE_RECORD_NOT_FOUND";

    /** 同一就诊已存在有效病案主档。 */
    public static final String CASE_RECORD_DUPLICATE_ENCOUNTER = "MRMS_CASE_RECORD_DUPLICATE_ENCOUNTER";

    /** 病案状态不允许当前操作。 */
    public static final String CASE_RECORD_INVALID_STATUS = "MRMS_CASE_RECORD_INVALID_STATUS";

    /** 首页已冻结不可编辑。 */
    public static final String HOMEPAGE_FROZEN = "MRMS_HOMEPAGE_FROZEN";

    /** 首页提交校验失败。 */
    public static final String HOMEPAGE_SUBMIT_VALIDATION = "MRMS_HOMEPAGE_SUBMIT_VALIDATION";

    /** 病案号发号失败。 */
    public static final String MEDCASNO_GENERATE_FAILED = "MRMS_MEDCASNO_GENERATE_FAILED";
}
