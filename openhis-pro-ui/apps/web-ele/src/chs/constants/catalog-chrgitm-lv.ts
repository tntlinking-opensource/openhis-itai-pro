/** 国家目录 chrgitm_lv（01/02/03）→ 药品维护表单 chrgitmLv（常见 1/2/3） */
export function catalogChrgitmLvToFormValue(code?: string | null): string | undefined {
  if (!code) return undefined;
  const trimmed = code.trim();
  const map: Record<string, string> = {
    '01': '1',
    '02': '2',
    '03': '3',
    甲类: '1',
    乙类: '2',
    丙类: '3',
  };
  return map[trimmed] ?? trimmed;
}
