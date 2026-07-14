/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.command;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.command.BatchCommand;

import java.time.LocalDateTime;

/**
 * 维护公费学生名单命令。
 */
@Getter
@Setter
public class UpsertPatientStudentRecordCommand extends BatchCommand<UpsertPatientStudentRecordCommand.UpsertPatientStudentRecordItemCommand> {

    /**
     * 学生记录维护子命令。
     */
    @Getter
    @Setter
    public static class UpsertPatientStudentRecordItemCommand {

        /**
         * 患者标识。
         */
        private Long patientId;

        /**
         * 学生记录标识。
         */
        private Long studentId;

        /**
         * 学号。
         */
        private String studentNo;

        /**
         * 学生姓名。
         */
        private String name;

        /**
         * 身份证号。
         */
        private String idCard;

        /**
         * 联系电话。
         */
        private String phone;

        /**
         * 学院。
         */
        private String college;

        /**
         * 专业。
         */
        private String major;

        /**
         * 学历层次。
         */
        private Integer eduLevel;

        /**
         * 入校时间。
         */
        private LocalDateTime enrollTime;

        /**
         * 离校时间。
         */
        private LocalDateTime leaveTime;

        /**
         * 年级。
         */
        private String grade;

        /**
         * 学习形式。
         */
        private Integer studyType;

        /**
         * 在校状态。
         */
        private Integer schoolStatus;

        /**
         * 体检结果。
         */
        private Integer physicalExamResult;

        /**
         * 辅导员。
         */
        private String counselor;

        /**
         * 辅导员电话。
         */
        private String counselorPhone;
    }
}
