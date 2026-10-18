/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.utils;

import com.zxplat.domain.share.constants.GeneralSharedConstants;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * 根据出生日期算年龄
 *
 * @date 2025/2/24
 */
public final class AgeCalculatorUtil {

    /** 根据出生日期与当前日期计算周岁。 */
    /**
     * calculateAge（AgeCalculatorUtil）。
     * @param birthDate birthDate（AgeCalculatorUtil 业务上下文）。
     * @return 执行结果
     */
    public static int calculateAge(LocalDate birthDate) {
        if (birthDate == null || LocalDate.now().isBefore(birthDate)) {
            return 0;
        }
        int years = LocalDate.now().getYear() - birthDate.getYear();
        if (LocalDate.now().getMonthValue() < birthDate.getMonthValue()
                || (LocalDate.now().getMonthValue() == birthDate.getMonthValue()
                && LocalDate.now().getDayOfMonth() < birthDate.getDayOfMonth())) {
            years--;
        }
        return Math.max(years, 0);
    }

//    /**
//     * 当前年龄取得（床位列表表示年龄用）
//     */
//    public static String getAge(Date date) {
//        // 将 Date 转换为 LocalDateTime
//        LocalDateTime dateTime = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
//        LocalDateTime now = LocalDateTime.now();
//        int years = now.getYear() - dateTime.getYear();
//        if (years > 2) {
//            return String.format("%d岁", years);
//        }
//
//        Period period = Period.between(dateTime.toLocalDate(), now.toLocalDate());
//        int months = period.getMonths();
//        int days = period.getDays();
//        long hours = ChronoUnit.HOURS.between(dateTime, now) - (days * 24L);
//
//        if (hours < 0) {
//            hours += 24;
//            days--;
//        }
//        if (days < 0) {
//            months--;
//            days = getLastDayOfMonth(dateTime) - dateTime.getDayOfMonth() + now.getDayOfMonth();
//        }
//        if (months < 0) {
//            months += 12;
//            years--;
//        }
//        if (years < 0) {
//            return "1小时";
//        }
//
//        if (years > 0 && months > 0) {
//            return String.format("%d岁%d月", years, months);
//        }
//        if (years > 0) {
//            return String.format("%d岁", years);
//        }
//        if (months > 0 && days > 0) {
//            return String.format("%d月%d天", months, days);
//        }
//        if (months > 0) {
//            return String.format("%d月", months);
//        }
//        if (days > 0 && hours > 0) {
//            return String.format("%d天%d小时", days, hours);
//        }
//        if (days > 0) {
//            return String.format("%d天", days);
//        }
//        if (hours > 0) {
//            return String.format("%d小时", hours);
//        }
//        return "1小时";
//    }
    /**
     * 复刻Oracle函数FUN_GET_AGE的核心逻辑：返回年龄字符串
     *
     * @param birthDate 出生日期
     * @return 年龄字符串（如：29岁、3岁5月、2月15天、18天），出生日期晚于当前日期返回空字符串
     */
    public static String getAge(Date birthDate) {
        // 入参校验
        if (birthDate == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }

        // 将Date转换为LocalDate（使用系统默认时区）
        LocalDate birthLocalDate = birthDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate currentDate = LocalDate.now();

        // 计算总天数（对应Oracle中的IDAY）
        long totalDays = ChronoUnit.DAYS.between(birthLocalDate, currentDate);

        // 若出生日期晚于当前日期，返回空字符串
        if (totalDays < 0) {
            return GeneralSharedConstants.EMPTY_STRING;
        }

        // 计算年份（复刻Oracle的闰年补偿逻辑：(当前年-出生年)/4 补偿闰年天数）
        int birthYear = birthLocalDate.getYear();
        int currentYear = currentDate.getYear();
        long leapYearCompensation = (currentYear - birthYear) / 4;
        long adjustedDays = totalDays - leapYearCompensation;

        // 计算年、月、天（按365天/年、30天/月粗略折算，与Oracle逻辑一致）
        int iYear = (int) (adjustedDays / 365);
        long remainingDaysAfterYear = adjustedDays - iYear * 365;
        int iMonth = (int) (remainingDaysAfterYear / 30);
        int iDay = (int) (remainingDaysAfterYear - iMonth * 30);

        // 按原函数规则拼接返回字符串
        if (iYear <= 0) {
            // 小于1岁
            if (iMonth <= 0) {
                // 小于1个月，返回X天
                return iDay + "天";
            } else {
                // 1个月及以上，返回X月X天
                return iMonth + "月" + iDay + "天";
            }
        } else {
            // 1岁及以上
            if (iYear < 5) {
                // 1-4岁
                if (iMonth <= 0) {
                    // 无整月，返回X岁X天
                    return iYear + "岁" + iDay + "天";
                } else {
                    // 有整月，返回X岁X月
                    return iYear + "岁" + iMonth + "月";
                }
            } else {
                // 5岁及以上，仅返回X岁
                return iYear + "岁";
            }
        }
    }
    /**
     * 处理LocalDateTime dateTime逻辑。
     * @return 返回结果
     */
    private static int getLastDayOfMonth(LocalDateTime dateTime) {
        int[] daysInMonth = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        if (isLeapYear(dateTime.getYear()) && dateTime.getMonthValue() == 2) {
            return 29;
        }
        return daysInMonth[dateTime.getMonthValue()];
    }

    /**
     * 处理int year逻辑。
     * @return 返回结果
     */
    private static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }
}
