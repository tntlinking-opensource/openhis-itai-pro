package com.openhis.whale.booking.domain.share;

/**
 * 停诊向导常量（07 §4）。
 */
public final class StopClinicConstants {

    /** 转交处置。 */
    public static final String DISPOSITION_TRANSFER = "TRANSFER";

    /** 改约处置。 */
    public static final String DISPOSITION_RESCHEDULE = "RESCHEDULE";

    /** 取消预约。 */
    public static final String DISPOSITION_CANCEL = "CANCEL";

    /** 稍后人工。 */
    public static final String DISPOSITION_MANUAL = "MANUAL";

    /** 停诊默认人工。 */
    public static final String STOP_MODE_MANUAL = "MANUAL";

    /** 同时段同号别。 */
    public static final String MATCH_SAME_SERVICE_SAME_SLOT = "SAME_SERVICE_SAME_SLOT";

    /** 同日最近可用时段。 */
    public static final String MATCH_SAME_SERVICE_NEAREST = "SAME_SERVICE_NEAREST";

    /** 同日同科室任意号别。 */
    public static final String MATCH_SAME_DEPT_ANY = "SAME_DEPT_ANY";

    /** 无需患者确认。 */
    public static final String CONFIRM_NONE = "NONE";

    /** 预览行：可自动转交。 */
    public static final String ROW_CAN_TRANSFER = "CAN_TRANSFER";

    /** 预览行：需人工。 */
    public static final String ROW_MANUAL_REQUIRED = "MANUAL_REQUIRED";

    /** 预览行：已挂号跳过。 */
    public static final String ROW_SKIP_REGISTERED = "SKIP_REGISTERED";

    /** 预览行：已报到/已叫号跳过。 */
    public static final String ROW_SKIP_CHECKED_IN = "SKIP_CHECKED_IN";

    private StopClinicConstants() {
    }
}
