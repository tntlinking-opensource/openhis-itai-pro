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

package com.openhis.health.application.basic.practitioner.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存医务人员签名图（adm_practitioner.signature）。
 */
@Getter
@Setter
public class SavePractitionerSignatureRequest {
    private Long practitionerId;
    /** data:image/*;base64,... 或纯 Base64；传空串表示清除 */
    private String signatureImage;
}
