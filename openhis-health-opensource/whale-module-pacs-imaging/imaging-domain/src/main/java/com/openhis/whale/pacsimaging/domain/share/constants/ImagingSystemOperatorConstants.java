/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.domain.share.constants;

/**
 * 影像模块系统级操作人常量。
 *
 * <p>C-STORE / Orthanc 等无登录态回调无法解析从业人员时，使用本常量作为审计操作人占位，
 * 避免业务代码散落裸 {@code 0L}。</p>
 */
public final class ImagingSystemOperatorConstants {

    /**
     * 系统回调占位操作人主键（无登录会话时回落）。
     *
     * <p>与病案 MRMS 等模块的系统操作人约定一致：{@code 0L} 表示非真人从业人员。</p>
     */
    public static final long SYSTEM_OPERATOR_ID = 0L;

    private ImagingSystemOperatorConstants() {
    }
}
