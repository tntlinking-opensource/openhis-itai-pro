/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 病案 4401 上传状态（主档冗余 + 日志）。
 */
public enum ChsUploadStatus implements EnumInterface {
/** 待上传（ChsUploadStatus，持久化编码 {@code pending}）。 */
    PENDING(0L, "pending", "待上传", "", 0L),
/** 上传成功（ChsUploadStatus，持久化编码 {@code success}）。 */
    SUCCESS(0L, "success", "上传成功", "", 1L),
/** 上传失败（ChsUploadStatus，持久化编码 {@code failed}）。 */
    FAILED(0L, "failed", "上传失败", "", 2L),
/** 无需上传（ChsUploadStatus，持久化编码 {@code skipped}）。 */
    SKIPPED(0L, "skipped", "无需上传", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举常量构造，绑定持久化 {@code code}、展示名与排序。
     *
     * @param codeSystemId 编码体系主键（预留）
     * @param code 持久化 code
     * @param display 界面展示名
     * @param definition 定义说明
     * @param sortOrder 排序号
     */
    ChsUploadStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    public Long getCodeSystemId() {
        return codeSystemId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public String getDefinition() {
        return definition;
    }

    public Long getSortOrder() {
        return sortOrder;
    }
}
