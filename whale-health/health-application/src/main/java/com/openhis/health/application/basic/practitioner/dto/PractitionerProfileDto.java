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

package com.openhis.health.application.basic.practitioner.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 医务人员主档维护（岗位工作台）：签名图等。
 */
@Getter
@Setter
public class PractitionerProfileDto {
    private Long practitionerId;
    private String name;
    private String busNo;
    private String statusEnum;
    /** 签名图 data URL 或纯 Base64；未维护时为 null */
    private String signatureImage;
    /** 是否已上传签名（列表/摘要场景可不返回大图） */
    private Boolean hasSignature;
}
