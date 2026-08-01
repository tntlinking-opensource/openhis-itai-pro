/** 国家目录 chrgitm_lv（01/02/03）→ 院内表单 chrgitmLv（与 yb_type 一致：1自费/2甲/3乙/4丙） */
export function catalogChrgitmLvToFormValue(code?: string | null): string | undefined {
  if (!code) return undefined;
  const trimmed = code.trim();
  const map: Record<string, string> = {
    '01': '2',
    '02': '3',
    '03': '4',
    '1': '2',
    '2': '3',
    '3': '4',
    自费: '1',
    甲类: '2',
    乙类: '3',
    丙类: '4',
  };
  return map[trimmed] ?? trimmed;
}
