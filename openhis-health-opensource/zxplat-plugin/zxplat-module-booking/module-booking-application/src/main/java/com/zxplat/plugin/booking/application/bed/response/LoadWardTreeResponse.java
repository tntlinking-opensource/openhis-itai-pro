/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.bed.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 病区树响应。
 */
@Getter
@Setter
public class LoadWardTreeResponse {

    /** 病区树节点列表。 */
    private List<WardTreeNodeDto> wards;
}
