/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.utils;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.concurrent.ThreadLocalRandom;


/**
 * 身份证号生成工具类
 *
 * @date 2025-07-24
 */
public class IdCardUtil {

    // 权重因子（用于计算校验码）
    private static final int[] WEIGHT_FACTORS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    // 校验码对应表
    private static final char[] CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    /**
     * 根据年龄生成类似身份证的唯一编码 格式：6位地区码 + 8位出生日期 + 3位随机码 + 1位校验码
     *
     * @param age 年龄（整数）
     * @return 18位身份证号
     */
    public static String generateIdByAge(int age) {
        // 1. 生成出生日期（确保当前年龄计算准确）
        LocalDate today = LocalDate.now();
        // 最大出生日期（今天减去年龄年）
        LocalDate maxBirthDate = today.minusYears(age);
        // 最小出生日期（最大日期减1年加1天，确保年龄计算准确）
        LocalDate minBirthDate = maxBirthDate.minusYears(1).plusDays(1);

        // 生成介于最小和最大日期之间的随机日期
        long minDay = minBirthDate.toEpochDay();
        long maxDay = maxBirthDate.toEpochDay();
        long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay + 1);

        String birthDateStr = LocalDate.ofEpochDay(randomDay).format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd

        // 2. 生成3位随机码（001-999）
        String randomCode = String.format("%03d", ThreadLocalRandom.current().nextInt(1, 1000));

        // 3. 组合前17位
        String partialId = GeneralSharedConstants.AREA_CODE + birthDateStr + randomCode;

        // 4. 计算校验码
        int sum = 0;
        for (int i = 0; i < partialId.length(); i++) {
            int digit = Character.getNumericValue(partialId.charAt(i));
            sum += digit * WEIGHT_FACTORS[i];
        }
        char checksum = CHECK_CODES[sum % 11];
        return partialId + checksum;
    }

    /**
     * 从身份证号提取生日日期
     *
     * @param idCard 身份证号码（15位或18位）
     * @return 格式化后的生日日期字符串，格式为"yyyy-MM-dd HH:mm:ss.SSS Z"
     * @throws IllegalArgumentException 如果身份证号格式无效
     */
    public static LocalDateTime extractBirthdayFromIdCard(String idCard) {
        // 验证身份证号长度
        if (idCard.length() != 15 && idCard.length() != 18) {
            throw new IllegalArgumentException("身份证号码长度无效，必须是15位或18位");
        }
        String birthdayStr;
        // 处理15位身份证号（7-12位是生日，年份只有后两位）
        if (idCard.length() == 15) {
            // 15位身份证：年份前加"19"，月份和日期直接取
            birthdayStr = "19" + idCard.substring(6, 8) + idCard.substring(8, 12);
        }
        // 处理18位身份证号（7-14位是完整生日）
        else {
            birthdayStr = idCard.substring(6, 14);
        }
        // 验证生日字符串是否全为数字
        if (!birthdayStr.matches("\\d{8}")) {
            throw new IllegalArgumentException("身份证号码中的生日部分无效");
        }
        // 解析生日日期
        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMdd");
        inputFormat.setLenient(false); // 严格模式，防止无效日期如2月30日
        try {
            // 解析为Date对象
            return inputFormat.parse(birthdayStr).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (ParseException e) {
            throw new IllegalArgumentException("无法解析生日日期: " + e.getMessage());
        }
    }

    /**
     * 根据身份证号计算年龄
     *
     * @param idCard 身份证号码（15位或18位）
     * @return 计算得到的年龄（整数）
     * @throws IllegalArgumentException 如果身份证号格式无效或包含非法字符
     */
    public static Integer calculateAgeFromIdCard(String idCard) {
        // 验证身份证号基本格式
        if (idCard == null || (idCard.length() != 15 && idCard.length() != 18)) {
            throw new IllegalArgumentException("身份证号码长度无效，必须是15位或18位");
        }

        // 验证身份证号字符合法性（仅包含数字和X）
        if (!idCard.matches("^[0-9Xx]+$")) {
            throw new IllegalArgumentException("身份证号码包含非法字符");
        }

        try {
            // 提取出生日期
            String birthYearStr, birthMonthStr, birthDayStr;

            if (idCard.length() == 15) {
                // 15位身份证：7-8位为年份（后两位），9-10位为月份，11-12位为日期
                birthYearStr = "19" + idCard.substring(6, 8);
                birthMonthStr = idCard.substring(8, 10);
                birthDayStr = idCard.substring(10, 12);
            } else {
                // 18位身份证：7-10位为年份，11-12位为月份，13-14位为日期
                birthYearStr = idCard.substring(6, 10);
                birthMonthStr = idCard.substring(10, 12);
                birthDayStr = idCard.substring(12, 14);
            }

            // 转换为整数
            int birthYear = Integer.parseInt(birthYearStr);
            int birthMonth = Integer.parseInt(birthMonthStr);
            int birthDay = Integer.parseInt(birthDayStr);

            // 验证日期有效性
            if (birthMonth < 1 || birthMonth > 12 || birthDay < 1 || birthDay > 31) {
                throw new IllegalArgumentException("身份证中的出生日期无效");
            }

            // 获取当前日期
            Calendar calendar = Calendar.getInstance();
            int currentYear = calendar.get(Calendar.YEAR);
            int currentMonth = calendar.get(Calendar.MONTH) + 1; // Calendar月份从0开始
            int currentDay = calendar.get(Calendar.DAY_OF_MONTH);

            // 计算年龄
            int age = currentYear - birthYear;

            // 判断是否已过生日
            if (currentMonth < birthMonth || (currentMonth == birthMonth && currentDay < birthDay)) {
                age--; // 未过生日，年龄减1
            }

            // 验证年龄合理性
            if (age < 0 || age > 150) {
                throw new IllegalArgumentException("计算得到的年龄超出合理范围");
            }

            return age;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("身份证号码中的日期部分格式错误", e);
        }
    }
}
