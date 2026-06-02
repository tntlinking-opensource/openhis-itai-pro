/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.emergency.patientfocus.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存或撤销就诊重点关注。
 */
@Getter
@Setter
public class SaveEmgPatientFocusCommand {

    private Long encounterId;

    private Long patientId;

    /** 关注说明；撤销时可为空。 */
    private String focusNote;

    /** true 表示撤销关注；false 表示设为关注（note 可为空）。 */
    private boolean revoke;

    private Long operatorId;
}
