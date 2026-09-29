/** 与主数据性别字典 value 对齐的二代证解析结果 */
export type IdCardGenderCode = 'FEMALE' | 'MALE';

/** 18 位大陆居民身份证格式（与患者档案 / 后端 ID_CARD_REGEX 对齐） */
const ID_CARD_18_REGEX =
  /^[1-9]\d{5}(?:18|19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dX]$/i;

/** 15 位大陆居民身份证格式 */
const ID_CARD_15_REGEX = /^[1-9]\d{7}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}$/;

/**
 * 是否符合 15/18 位大陆居民身份证号格式（空串视为不通过）。
 */
export function isValidChineseIdCard(value: unknown): boolean {
  const t = String(value ?? '').trim();
  if (!t) {
    return false;
  }
  return ID_CARD_18_REGEX.test(t) || ID_CARD_15_REGEX.test(t);
}

/**
 * 表单侧身份证输入是否可接受：空串通过（必填由 required 管）；
 * 非空须通过 {@link isValidChineseIdCard}。产品允许无证建档（id_card 可空）。
 */
export function isAcceptableChineseIdCardInput(value: unknown): boolean {
  const t = String(value ?? '').trim();
  if (!t) {
    return true;
  }
  return isValidChineseIdCard(t);
}

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
 * 由周岁年龄反推出生日期（今天减 N 年，与患者档案编辑同口径）。
 *
 * @param age 周岁
 * @param referenceDate 基准日，默认当天
 */
export function birthDateFromAgeYears(age: number, referenceDate: Date = new Date()): string {
  const year = referenceDate.getFullYear() - age;
  const month = String(referenceDate.getMonth() + 1).padStart(2, '0');
  const day = String(referenceDate.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
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
