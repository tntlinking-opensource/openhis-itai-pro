/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.command;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.command.BatchCommand;

import java.time.LocalDate;
import java.util.List;

/**
 * 修改患者档案命令。
 */
@Getter
@Setter
public class ModifyPatientProfileCommand extends BatchCommand<ModifyPatientProfileCommand.ModifyPatientProfileItemCommand> {

    /**
     * 修改患者档案子命令。
     */
    @Getter
    @Setter
    public static class ModifyPatientProfileItemCommand {

        /**
         * 患者标识。
         */
        private Long patientId;

        /**
         * 患者姓名。
         */
        private String name;

        /**
         * 姓名扩展 JSON。
         */
        private String nameJson;

        /**
         * 性别编码。
         */
        private String genderEnum;

        /**
         * 出生日期。
         */
        private LocalDate birthDate;

        /**
         * 身份证号。
         */
        private String idCard;

        /**
         * 婚姻状态编码。
         */
        private String maritalStatusEnum;

        /**
         * 职业编码。
         */
        private String prfsEnum;

        /**
         * 联系电话。
         */
        private String phone;

        /**
         * 地址全文。
         */
        private String address;

        /**
         * 地址省份。
         */
        private String addressProvince;

        /**
         * 地址城市。
         */
        private String addressCity;

        /**
         * 地址区县。
         */
        private String addressDistrict;

        /**
         * 地址街道。
         */
        private String addressStreet;

        /**
         * 地址扩展 JSON。
         */
        private String addressExtendJson;

        /**
         * 民族编码。
         */
        private String nationalityCode;

        /**
         * 工作单位。
         */
        private String workCompany;

        /**
         * 籍贯。
         */
        private String nativePlace;

        /**
         * 国家地区编码。
         */
        private String countryCode;

        /**
         * 联系人姓名。
         */
        private String linkName;

        /**
         * 联系人关系编码。
         */
        private Integer linkRelationCode;

        /**
         * 联系人电话。
         */
        private String linkTelcom;

        /**
         * 联系人扩展 JSON。
         */
        private String linkJsons;

        /**
         * ABO 血型编码。
         */
        private Integer bloodTypeAbo;

        /**
         * Rh 血型编码。
         */
        private Integer bloodTypeRh;

        /**
         * 非身份证证件集合。
         */
        private List<PatientIdentifierItemCommand> identifierItems;
    }
}
