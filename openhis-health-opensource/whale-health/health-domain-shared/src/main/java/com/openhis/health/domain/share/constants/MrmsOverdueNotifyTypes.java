/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.share.constants;

/**
 * MRMS 逾期通知类型码。
 */
public final class MrmsOverdueNotifyTypes {

    private MrmsOverdueNotifyTypes() {
    }

    /** 出院后未及时提交首页。 */
    public static final String SUBMIT_OVERDUE = "submit_overdue";

    /** 出院后未及时归档。 */
    public static final String ARCHIVE_OVERDUE = "archive_overdue";
}
