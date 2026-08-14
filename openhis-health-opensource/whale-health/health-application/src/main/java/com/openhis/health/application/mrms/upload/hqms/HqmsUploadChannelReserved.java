/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.hqms;

/**
 * HQMS 上报渠道预留包（本期不成码）。
 *
 * <p>与 {@code upload.medcas} 平级；落地时在此包下建 dto/request/service/support，
 * 出站经 health-infrastructure 对应 Extension，勿把渠道抬成 mrms L2。</p>
 */
public final class HqmsUploadChannelReserved {

    private HqmsUploadChannelReserved() {
    }
}
