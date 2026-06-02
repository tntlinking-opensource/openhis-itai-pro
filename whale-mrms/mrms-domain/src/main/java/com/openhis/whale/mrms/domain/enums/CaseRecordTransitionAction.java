package com.openhis.whale.mrms.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 病案主档状态迁移动作。
 */
@Getter
@RequiredArgsConstructor
public enum CaseRecordTransitionAction {
    SUBMIT("submit"),
    RECEIVE("receive"),
    RETURN("return"),
    START_QC("startQc"),
    ARCHIVE("archive"),
    UNARCHIVE("unarchive");

    private final String code;

    public static CaseRecordTransitionAction fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CaseRecordTransitionAction action : values()) {
            if (action.code.equals(code)) {
                return action;
            }
        }
        return null;
    }
}
