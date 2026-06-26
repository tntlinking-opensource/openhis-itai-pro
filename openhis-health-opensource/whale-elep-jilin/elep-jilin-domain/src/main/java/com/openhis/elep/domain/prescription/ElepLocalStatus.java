/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription;

/** 本地电子处方状态机。 */
public final class ElepLocalStatus {

    public static final String DRAFT = "DRAFT";
    public static final String PRE_CHECKED = "PRE_CHECKED";
    public static final String SIGNED = "SIGNED";
    public static final String UPLOADED = "UPLOADED";
    public static final String REVOKED = "REVOKED";
    public static final String FAILED = "FAILED";

    private ElepLocalStatus() {
    }
}
