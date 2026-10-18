/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.share;

import com.zxplat.plugin.campus.domain.shared.CampusArchiveConstants;

/**
 * 档案变更/调动应用层常量（委托 domain.shared，保持既有引用）。
 */
public final class CampusArchiveApplicationConstants {

    public static final String CHANGE_TYPE_CREATE = CampusArchiveConstants.CHANGE_TYPE_CREATE;
    public static final String CHANGE_TYPE_UPDATE = CampusArchiveConstants.CHANGE_TYPE_UPDATE;
    public static final String CHANGE_TYPE_SYNC = CampusArchiveConstants.CHANGE_TYPE_SYNC;

    public static final String CHANGE_SOURCE_MANUAL = CampusArchiveConstants.CHANGE_SOURCE_MANUAL;
    public static final String CHANGE_SOURCE_SYNC = CampusArchiveConstants.CHANGE_SOURCE_SYNC;
    public static final String CHANGE_SOURCE_SYSTEM = CampusArchiveConstants.CHANGE_SOURCE_SYSTEM;
    public static final String CHANGE_SOURCE_PEIS_ROSTER_IMPORT = CampusArchiveConstants.CHANGE_SOURCE_PEIS_ROSTER_IMPORT;

    public static final String TRANSFER_SOURCE_MANUAL = CampusArchiveConstants.TRANSFER_SOURCE_MANUAL;
    public static final String TRANSFER_TYPE_CAMPUS = CampusArchiveConstants.TRANSFER_TYPE_CAMPUS;
    public static final String TRANSFER_TYPE_COLLEGE = CampusArchiveConstants.TRANSFER_TYPE_COLLEGE;
    public static final String TRANSFER_TYPE_CAMPUS_COLLEGE = CampusArchiveConstants.TRANSFER_TYPE_CAMPUS_COLLEGE;
    public static final String TRANSFER_TYPE_DEPARTMENT = CampusArchiveConstants.TRANSFER_TYPE_DEPARTMENT;
    public static final String TRANSFER_TYPE_CAMPUS_DEPARTMENT = CampusArchiveConstants.TRANSFER_TYPE_CAMPUS_DEPARTMENT;

    public static final String FIELD_WILDCARD = CampusArchiveConstants.FIELD_WILDCARD;

    private CampusArchiveApplicationConstants() {
    }
}
