/** 与主数据性别字典 value 对齐的二代证解析结果 */
export type IdCardGenderCode = 'FEMALE' | 'MALE';

/**
 * 由出生日期（YYYY-MM-DD）计算周岁年龄字符串；非法日期返回空串。
 *
 * @param birthDate 出生日期，建议 YYYY-MM-DD
 * @param referenceDate 计算基准日，默认当天（便于单测）
 */
export function calcAgeFromBirthDate(birthDate: string, referenceDate: Date = new Date()): string {
  if (!birthDate) {
    return '';
  }
  const [yearText, monthText, dayText] = birthDate.split('-');
  const birthYear = Number.parseInt(yearText ?? '', 10);
  const birthMonth = Number.parseInt(monthText ?? '', 10);
  const birthDay = Number.parseInt(dayText ?? '', 10);
  if (
    Number.isNaN(birthYear) ||
    Number.isNaN(birthMonth) ||
    Number.isNaN(birthDay) ||
    birthMonth < 1 ||
    birthMonth > 12 ||
    birthDay < 1 ||
    birthDay > 31
  ) {
    return '';
  }
  const now = referenceDate;
  let age = now.getFullYear() - birthYear;
  const currentMonth = now.getMonth() + 1;
  const currentDay = now.getDate();
  const birthdayPassed =
    currentMonth > birthMonth || (currentMonth === birthMonth && currentDay >= birthDay);
  if (!birthdayPassed) {
    age -= 1;
  }
  return age >= 0 ? String(age) : '';
}

/**
 * 解析 18 位中国大陆二代居民身份证号中的出生日期与性别（第 17 位：奇男偶女）。
 * 不校验末位校验码；出生段非 8 位数字时返回 null。
 *
 * @param id 18 位证件号
 */
export function parseChineseIdCard18(
  id: string,
): null | { birthDate: string; genderCode: IdCardGenderCode } {
  if (!id || id.length !== 18) {
    return null;
  }
  const birthStr = id.slice(6, 14);
  if (!/^\d{8}$/.test(birthStr)) {
    return null;
  }
  const year = birthStr.slice(0, 4);
  const month = birthStr.slice(4, 6);
  const day = birthStr.slice(6, 8);
  const birthDate = `${year}-${month}-${day}`;
  const seqChar = id.charAt(16);
  if (!/\d/.test(seqChar)) {
    return null;
  }
  const genderDigit = Number.parseInt(seqChar, 10);
  if (Number.isNaN(genderDigit)) {
    return null;
  }
  const genderCode: IdCardGenderCode = genderDigit % 2 === 1 ? 'MALE' : 'FEMALE';
  return { birthDate, genderCode };
}
