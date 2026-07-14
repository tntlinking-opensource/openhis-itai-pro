/** 行政性别 code → 展示文案（与后端 AdministrativeGender 对齐） */
const GENDER_LABEL_BY_CODE: Record<string, string> = {
  MALE: '男性',
  FEMALE: '女性',
  UNKNOWN: '未知',
};

/**
 * 解析性别展示文案：优先后端 genderEnumText，再回落 genderEnum code。
 */
export function resolveAdministrativeGenderLabel(
  genderEnumText?: string | null,
  genderEnum?: string | null,
  emptyFallback = '—',
): string {
  const text = String(genderEnumText ?? '').trim();
  if (text) {
    return text;
  }
  const code = String(genderEnum ?? '').trim();
  if (!code) {
    return emptyFallback;
  }
  return GENDER_LABEL_BY_CODE[code] ?? code;
}
