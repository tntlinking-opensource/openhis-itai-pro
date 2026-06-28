/** 挂号待遇展示用字典（国家平台常用码，未知码保留原值） */

const FUND_PAY_TYPE_LABEL: Record<string, string> = {
  '310100': '职工基本医疗保险统筹基金',
  '310102': '职工门诊统筹基金',
  '310200': '职工基本医疗保险个人账户基金',
  '311700': '职工大病保险基金',
  '320100': '公务员医疗补助基金',
  '330100': '大额医疗费用补助基金',
  '340100': '离休人员医疗保障基金',
  '350100': '残疾军人医疗补助基金',
  '360100': '财政基金',
  '360200': '财政预支基金',
  '360300': '保健预支基金',
  '370100': '企业补充医疗保险基金',
  '390100': '城乡居民基本医疗保险基金',
  '390102': '居民门诊统筹基金',
  '390200': '城乡居民大病医疗保险基金',
  '390300': '城乡居民大病补充保险基金',
  '390400': '意外伤害基金',
  '311800': '城乡居民大病保险基金',
  '510100': '生育基金',
  '610100': '医疗救助基金',
  '610107': '医疗救助基金',
  '620100': '照护保险基金',
  '640100': '政府兜底基金',
  '999702': '职工大病保险基金',
  '999900': '医院垫付',
  '999996': '其他统筹基金',
  '999997': '其他基金',
  '99999702': '职工大病保险基金',
  '99999703': '长期护理基金',
};

const PSN_TYPE_LABEL: Record<string, string> = {
  '11': '在职',
  '12': '退休',
  '13': '离休',
  '14': '居民（未成年）',
  '15': '居民（成年）',
  '16': '居民（老年）',
  '1101': '职工在职',
  '1102': '公务员在职',
  '1103': '灵活就业人员在职',
  '1201': '职工退休',
  '1202': '公务员退休',
  '1203': '灵活就业人员退休',
  '1300': '离休人员',
  '1401': '新生儿',
  '1402': '学龄前儿童',
  '1403': '中小学生',
  '1404': '大学生',
  '1405': '未成年（未入学）',
  '1501': '普通居民（成年）',
  '156001': '低保人员',
};

const PSN_INSU_STAS_LABEL: Record<string, string> = {
  '0': '未参保',
  '1': '正常参保',
  '2': '暂停参保',
  '3': '终止参保',
  '4': '终止参保',
};

const CUM_TYPE_LABEL: Record<string, string> = {
  D310101: '职工基本医疗统筹支付累计',
  D310102: '职工基本医疗统筹自付累计',
  D310103: '职工门诊统筹支付累计',
  D310201: '职工个人账户支出累计',
  D390101: '居民基本医疗统筹支付累计',
  D390102: '居民基本医疗统筹自付累计',
  D390201: '居民大病保险支付累计',
};

function lookupLabel(code: string | null | undefined, map: Record<string, string>): string {
  const text = String(code ?? '').trim();
  if (!text) {
    return '—';
  }
  return map[text] ?? text;
}

export function formatFundPayType(code?: string | null): string {
  return lookupLabel(code, FUND_PAY_TYPE_LABEL);
}

export function formatPsnType(code?: string | null): string {
  return lookupLabel(code, PSN_TYPE_LABEL);
}

export function formatPsnInsuStas(code?: string | null): string {
  return lookupLabel(code, PSN_INSU_STAS_LABEL);
}

export function formatCumTypeCode(code?: string | null): string {
  return lookupLabel(code, CUM_TYPE_LABEL);
}
