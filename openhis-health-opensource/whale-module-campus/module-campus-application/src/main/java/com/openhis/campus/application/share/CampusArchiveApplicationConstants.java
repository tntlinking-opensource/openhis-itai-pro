/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.share;

/**
 * 档案变更/调动应用层常量。
 */
public final class CampusArchiveApplicationConstants {

    public static final String CHANGE_TYPE_CREATE = "CREATE";
    public static final String CHANGE_TYPE_UPDATE = "UPDATE";
    public static final String CHANGE_TYPE_SYNC = "SYNC";

    public static final String CHANGE_SOURCE_MANUAL = "MANUAL";
    public static final String CHANGE_SOURCE_SYNC = "SYNC";
    public static final String CHANGE_SOURCE_SYSTEM = "SYSTEM";
    public static final String CHANGE_SOURCE_PEIS_ROSTER_IMPORT = "PEIS_ROSTER_IMPORT";

    public static final String TRANSFER_SOURCE_MANUAL = "MANUAL";
    public static final String TRANSFER_TYPE_CAMPUS = "CAMPUS";
    public static final String TRANSFER_TYPE_COLLEGE = "COLLEGE";
    public static final String TRANSFER_TYPE_CAMPUS_COLLEGE = "CAMPUS_COLLEGE";
    public static final String TRANSFER_TYPE_DEPARTMENT = "DEPARTMENT";
    public static final String TRANSFER_TYPE_CAMPUS_DEPARTMENT = "CAMPUS_DEPARTMENT";

    public static final String FIELD_WILDCARD = "*";

    private CampusArchiveApplicationConstants() {
    }
}
