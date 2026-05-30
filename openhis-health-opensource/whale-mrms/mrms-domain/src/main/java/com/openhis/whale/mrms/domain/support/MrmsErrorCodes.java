package com.openhis.whale.mrms.domain.support;

/**
 * MRMS 业务错误码。
 */
public final class MrmsErrorCodes {

    private MrmsErrorCodes() {
    }

    public static final String CASE_RECORD_NOT_FOUND = "MRMS_CASE_NOT_FOUND";
    public static final String CASE_RECORD_EXISTS = "MRMS_CASE_EXISTS";
    public static final String HOMEPAGE_NOT_FOUND = "MRMS_HOMEPAGE_NOT_FOUND";
    public static final String HOMEPAGE_FROZEN = "MRMS_HOMEPAGE_FROZEN";
    public static final String INVALID_STATUS = "MRMS_INVALID_STATUS";
    public static final String NOT_EDITABLE = "MRMS_NOT_EDITABLE";
    public static final String VALIDATION_FAILED = "MRMS_VALIDATION_FAILED";
}
