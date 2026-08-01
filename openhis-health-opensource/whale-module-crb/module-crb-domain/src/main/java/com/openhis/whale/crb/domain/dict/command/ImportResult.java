/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict.command;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入汇总结果（成功 / 跳过 / 失败计数）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class ImportResult {

    /** 成功 upsert 行数。 */
    private int successCount;

    /** 跳过行数（空行等）。 */
    private int skipCount;

    /** 失败行数（校验或唯一冲突等）。 */
    private int failCount;

    /** 失败或跳过说明。 */
    private List<String> messages = new ArrayList<>();

    /**
     * 累加成功计数。
     */
    public void incrementSuccessCount() {
        successCount = successCount + 1;
    }

    /**
     * 累加跳过计数。
     */
    public void incrementSkipCount() {
        skipCount = skipCount + 1;
    }

    /**
     * 累加失败计数。
     */
    public void incrementFailCount() {
        failCount = failCount + 1;
    }

    /**
     * 追加一条汇总说明。
     *
     * @param message 说明文案
     */
    public void addMessage(String message) {
        if (message != null && !message.isBlank()) {
            messages.add(message);
        }
    }
}
