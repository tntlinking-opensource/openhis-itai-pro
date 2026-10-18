/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;

/**
 * 身份证号解析工具：从 15/18 位证件号提取生日与年龄。
 *
 * <p>产品约定：患者主档 {@code id_card} 允许为空；禁止按年龄合成虚假证件号。
 * 无证建档靠姓名/性别/生日（或年龄推生日）完成，不写占位身份证。</p>
 */
public class IdCardUtil {

    private IdCardUtil() {
    }

    /**
     * 从身份证号提取生日日期。
     *
     * @param idCard 身份证号码（15位或18位）
     * @return 生日对应的本地日期时间（当日 0 点语义由解析结果决定）
     * @throws IllegalArgumentException 如果身份证号格式无效
     */
    public static LocalDateTime extractBirthdayFromIdCard(String idCard) {
        // Gate：仅接受 15/18 位长度
        if (idCard.length() != 15 && idCard.length() != 18) {
            throw new IllegalArgumentException("身份证号码长度无效，必须是15位或18位");
        }
        String birthdayStr;
        // 分支：15 位年份仅两位，补 19
        if (idCard.length() == 15) {
            birthdayStr = "19" + idCard.substring(6, 8) + idCard.substring(8, 12);
        } else {
            birthdayStr = idCard.substring(6, 14);
        }
        // Gate：生日段须为 8 位数字
        if (!birthdayStr.matches("\\d{8}")) {
            throw new IllegalArgumentException("身份证号码中的生日部分无效");
        }
        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMdd");
        inputFormat.setLenient(false);
        try {
            return inputFormat.parse(birthdayStr).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (ParseException e) {
            throw new IllegalArgumentException("无法解析生日日期: " + e.getMessage());
        }
    }

    /**
     * 根据身份证号计算年龄。
     *
     * @param idCard 身份证号码（15位或18位）
     * @return 周岁年龄
     * @throws IllegalArgumentException 如果身份证号格式无效或包含非法字符
     */
    public static Integer calculateAgeFromIdCard(String idCard) {
        // Gate：长度与字符集
        if (idCard == null || (idCard.length() != 15 && idCard.length() != 18)) {
            throw new IllegalArgumentException("身份证号码长度无效，必须是15位或18位");
        }
        if (!idCard.matches("^[0-9Xx]+$")) {
            throw new IllegalArgumentException("身份证号码包含非法字符");
        }

        try {
            String birthYearStr;
            String birthMonthStr;
            String birthDayStr;

            // 分支：15/18 位出生段切分不同
            if (idCard.length() == 15) {
                birthYearStr = "19" + idCard.substring(6, 8);
                birthMonthStr = idCard.substring(8, 10);
                birthDayStr = idCard.substring(10, 12);
            } else {
                birthYearStr = idCard.substring(6, 10);
                birthMonthStr = idCard.substring(10, 12);
                birthDayStr = idCard.substring(12, 14);
            }

            int birthYear = Integer.parseInt(birthYearStr);
            int birthMonth = Integer.parseInt(birthMonthStr);
            int birthDay = Integer.parseInt(birthDayStr);

            // Gate：月日合理范围
            if (birthMonth < 1 || birthMonth > 12 || birthDay < 1 || birthDay > 31) {
                throw new IllegalArgumentException("身份证中的出生日期无效");
            }

            Calendar calendar = Calendar.getInstance();
            int currentYear = calendar.get(Calendar.YEAR);
            int currentMonth = calendar.get(Calendar.MONTH) + 1;
            int currentDay = calendar.get(Calendar.DAY_OF_MONTH);

            int age = currentYear - birthYear;
            // 分支：未过生日则周岁减一
            if (currentMonth < birthMonth || (currentMonth == birthMonth && currentDay < birthDay)) {
                age--;
            }

            // Gate：年龄合理范围
            if (age < 0 || age > 150) {
                throw new IllegalArgumentException("计算得到的年龄超出合理范围");
            }

            return age;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("身份证号码中的日期部分格式错误", e);
        }
    }
}
