package com.openhis.whale.mrms.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 病案主档状态，持久化 {@link #code}。
 */
@Getter
@RequiredArgsConstructor
public enum CaseRecordStatus {
    DRAFT("draft", "草稿"),
    SUBMITTED("submitted", "已提交"),
    RETURNED("returned", "已退回"),
    RECEIVED("received", "已接收"),
    QCING("qcing", "质控中"),
    ARCHIVED("archived", "已归档");

    private final String code;
    private final String display;

    public static CaseRecordStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CaseRecordStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
