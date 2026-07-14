/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.inventory.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【3501】盘存上传 input */
@Data
@Accessors(chain = true)
public class Inv3501Input {

    private InvInvinfo invinfo;
}
