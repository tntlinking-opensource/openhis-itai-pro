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

import java.util.List;

/**
 * 编辑医疗卫生人员命令（批量）。
 * <p>
 * 修改人员联系方式、地址、工号、签名等档案信息，可配合变更字段列表做增量更新。
 */
@Getter
@Setter
public class EditPractitionerCommand extends BatchCommand<EditPractitionerCommand.EditPractitionerItemCommand> {
    /** 执行本次批量编辑的操作人标识。 */
    private Long operatorId;

    /** 单条编辑项：目标人员与待更新属性。 */
    @Getter
    @Setter
    public static class EditPractitionerItemCommand {
        /** 被编辑的医疗卫生人员主键。 */
        private Long practitionerId;
        /** 人员姓名。 */
        private String name;
        /** 行政性别 code（{@link com.openhis.health.domain.share.enums.AdministrativeGender}）。 */
        private String genderEnum;
        /** 联系电话。 */
        private String phone;
        /** 联系地址命令对象（结构化地址）。 */
        private AddressCommand address;
        /** 工号或业务编号。 */
        private String busNo;
        /** 电子签名或签名资源标识。 */
        private String signature;
        /** 本次实际发生变更的字段名列表。 */
        private List<String> changedFields;
    }
}
