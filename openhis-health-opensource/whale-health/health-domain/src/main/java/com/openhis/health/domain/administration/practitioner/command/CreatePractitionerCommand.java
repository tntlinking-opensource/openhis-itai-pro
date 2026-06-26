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

package com.openhis.health.domain.administration.practitioner.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建医疗卫生人员命令（批量）。
 * <p>
 * 登记新的医师或卫技人员主数据，关联系统用户、所属机构及工号等。
 */
@Getter
@Setter
public class CreatePractitionerCommand extends BatchCommand<CreatePractitionerCommand.CreatePractitionerItemCommand> {
    /** 执行本次批量创建的操作人标识。 */
    private Long operatorId;

    /** 单条创建项：新人员的基础档案信息。 */
    @Getter
    @Setter
    public static class CreatePractitionerItemCommand {
        /** 关联的系统用户主键。 */
        private Long userId;
        /** 人员姓名。 */
        private String name;
        /** 性别枚举编码。 */
        private String genderEnum;
        /** 工号或业务编号。 */
        private String busNo;
        /** 所属组织机构主键。 */
        private Long orgId;
        /** 医保人员编号。 */
        private String ybNo;
        /** 职称编码。 */
        private Integer drProfttlCode;
        /** 医师/药师执业资格证编号。 */
        private String pharPracCertNo;
    }
}
