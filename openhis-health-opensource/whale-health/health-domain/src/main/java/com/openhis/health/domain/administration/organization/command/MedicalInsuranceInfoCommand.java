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

package com.openhis.health.domain.administration.organization.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 机构医保定点信息命令片段：随创建/修改机构一并提交。
 */
@Getter
@Setter
public class MedicalInsuranceInfoCommand {
    /** 医保机构编号。 */
    private String ybNo;
    /** 医保机构名称。 */
    private String ybName;
    /** 医药机构标识（国家医保编码体系）。 */
    private String medinsId;
    /** 医保区划。 */
    private String medinsAdmdvs;
    /** 医药机构类型。 */
    private String medinsType;
    /** 医药机构等级。 */
    private String medinsLv;
}
