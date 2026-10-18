/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.utils;

import com.zxplat.domain.share.constants.AggregateTypeConstants;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 库存明细备份批次单号（{@code ABU}+日期+序号）日期段解析与前缀拼装。
 *
 * <p>与 {@link com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService#getSeqByDay} 对齐：
 * 日期段为 {@link DateTimeFormatter#ISO_LOCAL_DATE}（{@code yyyy-MM-dd}）。</p>
 */
public final class InventoryDetailSnapshotBusNoSupport {

    private static final int DATE_SEGMENT_LENGTH = 10;

    private InventoryDetailSnapshotBusNoSupport() {
    }

    /**
     * 将自然日格式化为备份单号日期段（{@code yyyy-MM-dd}）。
     *
     * @param localDate 自然日
     * @return 日期段
     */
    public static String formatDateSegment(LocalDate localDate) {
        return localDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * 拼装当自然日备份单号前缀（{@code ABU}+{@code yyyy-MM-dd}）。
     *
     * @param localDate 自然日
     * @return 前缀
     */
    public static String formatDayPrefix(LocalDate localDate) {
        return AggregateTypeConstants.INVENTORY_DETAIL_SNAPSHOT_BACKUP + formatDateSegment(localDate);
    }

    /**
     * 自备份单号解析自然日（{@code ABU}+{@code yyyy-MM-dd}+序号）。
     *
     * @param backupBusNo 备份单号
     * @return 自然日
     * @throws IllegalArgumentException 单号非法或日期段无法解析
     */
    public static LocalDate parseBackupDate(String backupBusNo) {
        if (backupBusNo == null || backupBusNo.isBlank()) {
            throw new IllegalArgumentException("backupBusNo 不能为空");
        }
        String trimmed = backupBusNo.trim();
        String prefix = AggregateTypeConstants.INVENTORY_DETAIL_SNAPSHOT_BACKUP;
        if (!trimmed.startsWith(prefix)) {
            throw new IllegalArgumentException("备份单号前缀非法：" + trimmed);
        }
        String afterPrefix = trimmed.substring(prefix.length());
        if (afterPrefix.length() < DATE_SEGMENT_LENGTH) {
            throw new IllegalArgumentException("备份单号日期段非法：" + trimmed);
        }
        String dateSegment = afterPrefix.substring(0, DATE_SEGMENT_LENGTH);
        try {
            return LocalDate.parse(dateSegment, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("备份单号日期段无法解析：" + dateSegment, ex);
        }
    }
}
