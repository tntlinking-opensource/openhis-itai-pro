/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * MRMS 病案模块业务错误码常量。
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

    /** 质控缺陷不存在。 */
    public static final String QC_DEFECT_NOT_FOUND = "MRMS_QC_DEFECT_NOT_FOUND";

    /** 质控缺陷状态不允许当前操作。 */
    public static final String QC_DEFECT_INVALID_STATUS = "MRMS_QC_DEFECT_INVALID_STATUS";

    /** 存在阻断级开放缺陷，不可归档。 */
    public static final String QC_ARCHIVE_BLOCKED = "MRMS_QC_ARCHIVE_BLOCKED";

    /** 卷宗预览权限不足或病案状态不允许。 */
    public static final String VOLUME_ACCESS_DENIED = "MRMS_VOLUME_ACCESS_DENIED";

    /** 借阅申请不存在。 */
    public static final String BORROW_NOT_FOUND = "MRMS_BORROW_NOT_FOUND";

    /** 借阅申请状态不允许当前操作。 */
    public static final String BORROW_INVALID_STATUS = "MRMS_BORROW_INVALID_STATUS";

    /** 4401 上传前置条件不满足。 */
    public static final String UPLOAD_PRECONDITION = "MRMS_UPLOAD_PRECONDITION";

    /** 4401 组装校验失败。 */
    public static final String UPLOAD_ASSEMBLE_VALIDATION = "MRMS_UPLOAD_ASSEMBLE_VALIDATION";

    /** 首页手术保存：主手术标志超过一条（AS-MRMS-HP-001 EX-MRMS-HP-PR-203）。 */
    public static final String HOMEPAGE_PROCEDURE_MAIN_DUPLICATE = "EX-MRMS-HP-PR-203";

    /** 4401/4101A 手术行无医保对照（AS-MRMS-UP-001 EX-MRMS-UP-OP-201）。 */
    public static final String UPLOAD_PROCEDURE_YB_UNMAPPED = "EX-MRMS-UP-OP-201";

    /** 4401/4101A 手术编码为空（AS-MRMS-UP-001 EX-MRMS-UP-OP-202）。 */
    public static final String UPLOAD_PROCEDURE_CODE_EMPTY = "EX-MRMS-UP-OP-202";

    /** 非病案管理员不可归档/解档病例。 */
    public static final String CLINICAL_ARCHIVE_ROLE_REQUIRED = "MRMS_CLINICAL_ARCHIVE_ROLE_REQUIRED";

    /** 病案主档已归档时禁止单独解档病例（须走病案解档）。 */
    public static final String CLINICAL_UNARCHIVE_BLOCKED_BY_CASE_RECORD =
            "MRMS_CLINICAL_UNARCHIVE_BLOCKED_BY_CASE_RECORD";

    /** EMR 未启用，无法归档/解档病例。 */
    public static final String CLINICAL_ARCHIVE_EMR_DISABLED = "MRMS_CLINICAL_ARCHIVE_EMR_DISABLED";

    /** 首页编辑申请不存在。 */
    public static final String HOMEPAGE_EDIT_NOT_FOUND = "MRMS_HOMEPAGE_EDIT_NOT_FOUND";

    /** 首页编辑申请状态不允许当前操作。 */
    public static final String HOMEPAGE_EDIT_INVALID_STATUS = "MRMS_HOMEPAGE_EDIT_INVALID_STATUS";

    /** 同病案已有在途编辑申请。 */
    public static final String HOMEPAGE_EDIT_ACTIVE_EXISTS = "MRMS_HOMEPAGE_EDIT_ACTIVE_EXISTS";

    /** 编辑申请版本与主档不一致（并发）。 */
    public static final String HOMEPAGE_EDIT_VERSION_CONFLICT = "MRMS_HOMEPAGE_EDIT_VERSION_CONFLICT";
}
