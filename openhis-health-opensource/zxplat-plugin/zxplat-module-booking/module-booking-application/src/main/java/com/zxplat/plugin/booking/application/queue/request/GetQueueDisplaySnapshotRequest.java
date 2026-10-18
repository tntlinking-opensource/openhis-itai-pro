/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.queue.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 叫号大屏快照请求。
 */
@Getter
@Setter
public class GetQueueDisplaySnapshotRequest {

    /** 机构主键。 */
    @NotNull
    private Long organizationId;

    /** 诊室/去向地点主键。 */
    @NotNull
    private Long destinationLocationId;
}
