/**
 * 4103 平台回包展示：状态映射、setlinfo 中文标签、子表抽取。
 * 展示用文案；业务分支须用码（stas_type / diag_type 等）。
 */

/** 平台审核状态文案（4102 规范 0～3） */
const STAS_TYPE_LABEL: Record<string, string> = {
  '0': '未提交',
  '1': '已提交',
  '2': '审核通过',
  '3': '审核不通过',
};

/** setlinfo 字段中文名（对齐 4103 规范；未知键回落原字段名） */
export const SETLINFO_FIELD_LABELS: Record<string, string> = {
  setl_id: '结算 ID',
  setl_list_sn: '清单流水号',
  setl_list_id: '清单流水号',
  psn_no: '人员编号',
  mdtrt_id: '就诊 ID',
  fixmedins_name: '定点机构名称',
  fixmedins_code: '定点机构编号',
  hi_setl_lv: '医保结算等级',
  medcasno: '病案号',
  dcla_time: '申报时间',
  psn_name: '人员姓名',
  gend: '性别',
  brdy: '出生日期',
  age: '年龄',
  nwb_age: '不足1岁年龄(天)',
  ntly: '国籍',
  naty: '民族',
  patn_cert_type: '证件类别',
  certno: '证件号码',
  prfs: '职业',
  curr_addr: '现住址',
  emp_name: '单位名称',
  emp_addr: '单位地址',
  emp_tel: '单位电话',
  poscode: '邮编',
  coner_name: '联系人姓名',
  patn_rlts: '与患者关系',
  coner_addr: '联系人地址',
  coner_tel: '联系人电话',
  insutype: '险种类型',
  sp_psn_type: '特殊人员类型',
  insu_admdvs: '参保医保区划',
  nwb_adm_type: '新生儿入院类型',
  nwbbirwt: '新生儿出生体重',
  nwbadmwt: '新生儿入院体重',
  mul_nwb_bir_wt: '多新生儿出生体重',
  mul_nwb_adm_wt: '多新生儿入院体重',
  opsp_diag_caty: '门慢门特诊断科别',
  opsp_mdtrt_date: '门慢门特就诊日期',
  ipt_med_type: '住院医疗类型',
  adm_way: '入院途径',
  trt_type: '治疗类别',
  adm_time: '入院时间',
  dscg_time: '出院时间',
  adm_caty: '入院科别',
  refldept_dept: '转科科别',
  dscg_caty: '出院科别',
  act_ipt_days: '实际住院天数',
  otp_wm_dise: '门急诊西医诊断',
  wm_dise_code: '西医诊断代码',
  otp_tcm_dise: '门急诊中医诊断',
  tcm_dise_code: '中医诊断代码',
  diag_code_cnt: '诊断代码计数',
  oprn_oprt_code_cnt: '手术操作代码计数',
  vent_used_days: '呼吸机使用天数',
  vent_used_h_cnt: '呼吸机使用小时',
  vent_used_m_cnt: '呼吸机使用分钟',
  bfadm_coma_days: '入院前昏迷天数',
  bfadm_coma_h_cnt: '入院前昏迷小时',
  bfadm_coma_m_cnt: '入院前昏迷分钟',
  afadm_coma_days: '入院后昏迷天数',
  afadm_coma_h_cnt: '入院后昏迷小时',
  afadm_coma_m_cnt: '入院后昏迷分钟',
  bld_cat: '输血品种',
  bld_amt: '输血量',
  bld_unt: '输血单位',
  spga_nurscare_days: '特级护理天数',
  lv1_nurscare_days: '一级护理天数',
  scd_nurscare_days: '二级护理天数',
  lv3_nurscare_days: '三级护理天数',
  dscg_way: '离院方式',
  acp_medins_name: '拟接收机构名称',
  acp_optins_code: '拟接收机构代码',
  days_rinp_flag_31: '出院31天再住院标志',
  days_rinp_pup_31: '出院31天再住院目的',
  chfpdr_name: '主诊医师姓名',
  chfpdr_code: '主诊医师代码',
  biz_sn: '业务流水号',
  bill_code: '票据代码',
  bill_no: '票据号码',
  setl_begn_date: '结算开始日期',
  setl_end_date: '结算结束日期',
  psn_selfpay: '个人自付',
  psn_ownpay: '个人自费',
  acct_pay: '个人账户支出',
  psn_cashpay: '个人现金支付',
  hi_paymtd: '医保支付方式',
  medins_fill_dept: '填报部门',
  medins_fill_psn: '填报人',
  vali_flag: '有效标志',
  rid: '唯一记录号',
  updt_time: '更新时间',
  crter_id: '创建人',
  crter_name: '创建人姓名',
  crte_time: '创建时间',
  crte_optins_no: '创建机构',
  opter_id: '经办人 ID',
  opter_name: '经办人姓名',
  opt_time: '经办时间',
  optins_no: '经办机构',
  poolarea: '统筹区编码',
  hi_no: '医保编号',
  hi_type: '医保类型',
  opsp_dise_name: '门慢门特病种名称',
  opsp_dise_code: '门慢门特病种代码',
  dscg_diag: '出院诊断',
  resp_nurs_name: '责任护士姓名',
  resp_nurs_code: '责任护士代码',
  hsorg_opter_code: '医保经办人代码',
  stas_type: '平台状态',
  setl_list_stas: '清单状态',
  hsorg_opter: '医保机构经办人',
  hsorg_name: '医保机构',
  hsorg_code: '医保机构代码',
  chk_cont: '审核内容',
  medfee_sumamt: '医疗费总额',
};

/** 收费项目类别常见码 */
const MED_CHRGITM_TYPE_LABEL: Record<string, string> = {
  '01': '床位费',
  '02': '诊察费',
  '03': '检查费',
  '04': '化验费',
  '05': '治疗费',
  '06': '手术费',
  '07': '护理费',
  '08': '卫生材料费',
  '09': '西药费',
  '10': '中药饮片费',
  '11': '中成药费',
  '12': '一般诊疗费',
  '13': '挂号费',
  '14': '其他费',
};

/**
 * 字段 → 码表（国家医保基线 + 院内首页常用口径；展示「中文（码）」）。
 * 真 ID（setl_id/mdtrt_id 等）不进本表，原样展示。
 */
const FIELD_CODE_LABELS: Record<string, Record<string, string>> = {
  gend: { '0': '未知', '1': '男', '2': '女', '9': '未说明' },
  stas_type: STAS_TYPE_LABEL,
  setl_list_stas: STAS_TYPE_LABEL,
  /** 入院病情 adm_cond_type / adm_cond_code */
  adm_cond_type: { '1': '有', '2': '临床未确定', '3': '情况不明', '4': '无' },
  adm_cond_code: { '1': '有', '2': '临床未确定', '3': '情况不明', '4': '无' },
  /** 入院途径 */
  adm_way: {
    '1': '急诊',
    '2': '门诊',
    '3': '其他医疗机构转入',
    '9': '其他',
  },
  /** 离院方式 */
  dscg_way: {
    '1': '医嘱离院',
    '2': '医嘱转院',
    '3': '医嘱转社区卫生服务机构/乡镇卫生院',
    '4': '非医嘱离院',
    '5': '死亡',
    '9': '其他',
  },
  /** 住院医疗类型 */
  ipt_med_type: { '1': '住院', '2': '日间手术' },
  /** 治疗类别 */
  trt_type: { '1': '中医', '2': '中西医', '3': '西医' },
  /** 与患者关系（医保 patn_rlts） */
  patn_rlts: {
    '01': '本人',
    '1': '本人',
    '02': '户主',
    '2': '户主',
    '10': '配偶',
    '11': '夫',
    '12': '妻',
    '20': '子',
    '21': '独生子',
    '22': '长子',
    '23': '次子',
    '24': '三子',
    '25': '四子',
    '26': '五子',
    '27': '养子或继子',
    '28': '女婿',
    '29': '其他儿子',
    '30': '女',
    '31': '独生女',
    '32': '长女',
    '33': '次女',
    '34': '三女',
    '35': '四女',
    '36': '五女',
    '37': '养女或继女',
    '38': '儿媳',
    '39': '其他女儿',
    '40': '孙子、孙女、外孙子、外孙女',
    '41': '孙子',
    '42': '孙女',
    '43': '外孙子',
    '44': '外孙女',
    '45': '孙媳妇或外孙媳妇',
    '46': '孙女婿或外孙女婿',
    '47': '曾孙子或外曾孙子',
    '48': '曾孙女或外曾孙女',
    '49': '其他孙子、孙女、外孙子、外孙女',
    '50': '父母',
    '51': '父亲',
    '52': '母亲',
    '53': '公公',
    '54': '婆婆',
    '55': '岳父',
    '56': '岳母',
    '57': '继父或养父',
    '58': '继母或养母',
    '59': '其他父母关系',
    '60': '祖父母、外祖父母',
    '61': '祖父',
    '62': '祖母',
    '63': '外祖父',
    '64': '外祖母',
    '65': '配偶的祖父母、外祖父母',
    '66': '曾祖父',
    '67': '曾祖母',
    '68': '配偶的曾祖父母、外曾祖父母',
    '69': '其他祖父母和外祖父母关系',
    '70': '兄、弟、姐、妹',
    '71': '兄',
    '72': '嫂',
    '73': '弟',
    '74': '弟媳',
    '75': '姐姐',
    '76': '姐夫',
    '77': '妹妹',
    '78': '妹夫',
    '79': '其他兄弟姐妹',
    '80': '其他',
    '81': '伯父',
    '82': '伯母',
    '83': '叔父',
    '84': '婶母',
    '85': '舅父',
    '86': '舅母',
    '87': '姨父',
    '88': '姨母',
    '89': '姑父',
    '90': '姑母',
    '91': '堂兄弟、堂姐妹',
    '92': '表兄弟、表姐妹',
    '93': '侄子',
    '94': '侄女',
    '95': '外甥',
    '96': '外甥女',
    '97': '其他亲属',
    '99': '非亲属',
  },
  /** 证件类别 */
  patn_cert_type: {
    '01': '居民身份证',
    '02': '中国人民解放军军官证',
    '03': '中国人民武装警察部队警官证',
    '04': '香港特区护照/身份证明',
    '05': '澳门特区护照/身份证明',
    '06': '台湾居民来往大陆通行证',
    '07': '外国人永久居留证',
    '08': '外国人护照',
    '09': '残疾人证',
    '10': '军烈属证明',
    '11': '外国人就业证',
    '12': '外国专家证',
    '13': '外国人常驻记者证',
    '14': '台港澳人员就业证',
    '15': '回国（来华）定居专家证',
    '16': '中国护照',
    '17': '港澳台居民居住证',
    '90': '社会保障卡',
    '99': '其他身份证件',
  },
  /** 险种类型 INSUTYPE */
  insutype: {
    '310': '职工基本医疗保险',
    '320': '城乡居民基本医疗保险',
    '330': '生育保险',
    '340': '离休人员医疗保障',
    '350': '城乡居民大病保险',
    '360': '公务员医疗补助',
    '370': '医疗救助',
    '380': '新型农村合作医疗',
    '390': '城乡居民医疗保险',
    '410': '一至六级残疾军人医疗补助',
    '510': '生育保险',
  },
  /** 医保支付方式 */
  hi_paymtd: {
    '1': '按项目',
    '2': '单病种',
    '3': '按病种分值',
    '4': '疾病诊断相关分组（DRG）',
    '5': '按床日',
    '6': '按人头',
    '7': '按定额',
  },
  /** 有效标志 */
  vali_flag: { '0': '无效', '1': '有效' },
  /** 出院31天再住院计划 */
  days_rinp_flag_31: { '1': '有', '2': '无' },
  /** 医保结算等级 */
  hi_setl_lv: { '1': '一级', '2': '二级', '3': '三级' },
  /** 医保类型 MDCS_TYPE（常见） */
  hi_type: {
    '1': '职工基本医疗保险',
    '2': '城乡居民基本医疗保险',
    '3': '其他医疗保险',
  },
  /** 国籍（常见） */
  ntly: { '156': '中国', '999': '其他' },
  /** 民族（常见） */
  naty: {
    '01': '汉族',
    '02': '蒙古族',
    '03': '回族',
    '04': '藏族',
    '05': '维吾尔族',
    '11': '满族',
    '99': '其他',
  },
  /** 职业（首页常用） */
  prfs: {
    '11': '国家机关、党群组织、企业、事业单位负责人',
    '13': '专业技术人员',
    '17': '办事人员和有关人员',
    '21': '商业、服务业人员',
    '27': '农、林、牧、渔、水利业生产人员',
    '31': '生产、运输设备操作人员及有关人员',
    '37': '军人',
    '51': '不便分类的其他从业人员',
    '54': '个体经营者',
    '70': '无业人员',
    '80': '退（离）休人员',
    '90': '学生',
  },
  /** 特殊人员类型（常见） */
  sp_psn_type: {
    '0': '非特殊人员',
    '1': '特困人员',
    '2': '城乡低保对象',
    '3': '农村五保户',
    '9': '其他',
  },
  /** 主手术/主诊标志 */
  main_oprn_flag: { '0': '否', '1': '是' },
  maindiag_flag: { '0': '否', '1': '主诊' },
  diag_type: { '1': '西医', '2': '中医主病', '3': '中医主证' },
  med_chrgitm_type: MED_CHRGITM_TYPE_LABEL,
};

/**
 * 码 →「中文（码）」；无映射则原样。
 */
export function labelWithCode(map: Record<string, string> | undefined, value: unknown): string {
  const code = String(value ?? '').trim();
  if (!code) {
    return '—';
  }
  if (!map) {
    return code;
  }
  const label = map[code];
  if (!label) {
    return code;
  }
  // 文案已含码时不再重复括号
  if (label.includes(code)) {
    return label;
  }
  return `${label}（${code}）`;
}

/**
 * 按字段名翻译码值（子表列也可复用）。
 */
export function formatFieldCodeValue(fieldKey: string, value: unknown): string {
  return labelWithCode(FIELD_CODE_LABELS[fieldKey], value);
}

export interface Settle4103FieldRow {
  key: string;
  label: string;
  value: string;
}

/**
 * 解析平台审核状态码（优先专用码；文案若为 0～3 也当码）。
 */
export function resolveStasTypeCode(code?: string | null, text?: string | null): string {
  const fromCode = String(code ?? '').trim();
  if (fromCode) {
    return fromCode;
  }
  const fromText = String(text ?? '').trim();
  if (fromText === '0' || fromText === '1' || fromText === '2' || fromText === '3') {
    return fromText;
  }
  return '';
}

/**
 * 平台审核状态中文（禁止把裸码当文案直接展示）。
 */
export function formatStasTypeLabel(code?: string | null, text?: string | null): string {
  const resolved = resolveStasTypeCode(code, text);
  if (resolved && STAS_TYPE_LABEL[resolved]) {
    return STAS_TYPE_LABEL[resolved];
  }
  const label = String(text ?? '').trim();
  // 文案本身已是中文且非纯数字码
  if (label && !/^[0-3]$/.test(label)) {
    return label;
  }
  return resolved || '—';
}

/**
 * 平台审核状态 Tag 色（按码）。
 */
export function stasTypeTagType(
  code?: string | null,
  text?: string | null,
): 'success' | 'danger' | 'warning' | 'info' {
  const resolved = resolveStasTypeCode(code, text);
  if (resolved === '2') return 'success';
  if (resolved === '3') return 'danger';
  if (resolved === '1') return 'warning';
  return 'info';
}

/**
 * 金额两位小数。
 */
export function formatSettle4103Money(value: unknown): string {
  if (value == null || value === '') return '—';
  const num = Number(value);
  if (!Number.isFinite(num)) return String(value);
  return num.toFixed(2);
}

/**
 * 诊断类别文案。
 */
export function formatDiagTypeLabel(value: unknown): string {
  return formatFieldCodeValue('diag_type', value);
}

/**
 * 主诊标志文案。
 */
export function formatMaindiagFlagLabel(value: unknown): string {
  return formatFieldCodeValue('maindiag_flag', value);
}

/**
 * 主手术标志文案。
 */
export function formatMainOprnFlagLabel(value: unknown): string {
  return formatFieldCodeValue('main_oprn_flag', value);
}

/**
 * 入院病情文案。
 */
export function formatAdmCondTypeLabel(value: unknown): string {
  return formatFieldCodeValue('adm_cond_type', value);
}

/**
 * 收费项目类别文案（数字码补齐两位：1→01；合计行原样）。
 */
export function formatMedChrgitmTypeLabel(value: unknown): string {
  const raw = String(value ?? '').trim();
  if (!raw) {
    return '—';
  }
  if (!/^\d+$/.test(raw)) {
    return raw;
  }
  const padded = raw.length === 1 ? `0${raw}` : raw;
  return formatFieldCodeValue('med_chrgitm_type', padded);
}

/**
 * 有效标志文案。
 */
export function formatValiFlagLabel(value: unknown): string {
  return formatFieldCodeValue('vali_flag', value);
}

/**
 * setlinfo 单字段展示值（字典码转中文；金额格式化；真 ID 原样）。
 */
export function formatSetlinfoCellValue(key: string, raw: unknown): string {
  if (raw == null) return '—';
  if (typeof raw === 'object') {
    try {
      return JSON.stringify(raw);
    } catch {
      return String(raw);
    }
  }
  const text = String(raw).trim();
  if (!text) return '—';
  if (
    key === 'psn_selfpay' ||
    key === 'psn_ownpay' ||
    key === 'acct_pay' ||
    key === 'psn_cashpay' ||
    key === 'medfee_sumamt'
  ) {
    return formatSettle4103Money(text);
  }
  // 有码表则「中文（码）」；无则原样（setl_id/mdtrt_id 等流水号）
  if (FIELD_CODE_LABELS[key]) {
    return formatFieldCodeValue(key, text);
  }
  return text;
}

/**
 * 从 setlinfo Map 生成描述行（有值优先；空值也列出关键字段时可扩展，此处列全部非空）。
 */
export function buildSetlinfoFieldRows(
  setlinfo?: Record<string, unknown> | null,
): Settle4103FieldRow[] {
  if (!setlinfo || typeof setlinfo !== 'object') {
    return [];
  }
  const rows: Settle4103FieldRow[] = [];
  const keys = Object.keys(setlinfo);
  // 已知字段按规范顺序优先，其余键追加
  const ordered: string[] = [];
  for (const key of Object.keys(SETLINFO_FIELD_LABELS)) {
    if (Object.prototype.hasOwnProperty.call(setlinfo, key)) {
      ordered.push(key);
    }
  }
  for (const key of keys) {
    if (!ordered.includes(key)) {
      ordered.push(key);
    }
  }
  for (const key of ordered) {
    const raw = setlinfo[key];
    if (raw == null || raw === '') {
      continue;
    }
    rows.push({
      key,
      label: SETLINFO_FIELD_LABELS[key] || key,
      value: formatSetlinfoCellValue(key, raw),
    });
  }
  return rows;
}

/**
 * 取平台 output 下某数组节点。
 */
export function pickPlatformArray(
  platformOutput: Record<string, unknown> | null | undefined,
  nodeName: string,
): Array<Record<string, unknown>> {
  if (!platformOutput || typeof platformOutput !== 'object') {
    return [];
  }
  const raw = platformOutput[nodeName];
  if (!Array.isArray(raw)) {
    return [];
  }
  const rows: Array<Record<string, unknown>> = [];
  for (const item of raw) {
    if (item && typeof item === 'object') {
      rows.push(item as Record<string, unknown>);
    }
  }
  return rows;
}

/**
 * 是否 iteminfo「金额合计」行（与分类行并列时不可再累加）。
 */
function isIteminfoTotalRow(row: Record<string, unknown>): boolean {
  const type = String(row.med_chrgitm_type ?? '').trim();
  return type.includes('合计');
}

/**
 * 是否收费项目分类码行（01～14 等数字码）。
 */
function isIteminfoCategoryRow(row: Record<string, unknown>): boolean {
  const type = String(row.med_chrgitm_type ?? '').trim();
  if (!type || isIteminfoTotalRow(row)) {
    return false;
  }
  return /^\d+$/.test(type);
}

/**
 * 从 iteminfo 解析医疗费总额：优先「金额合计」行，否则只加分类码行。
 * （吉林 4103 把合计行与 01～14 并列，全量相加会翻倍。）
 */
export function sumIteminfoAmt(platformOutput?: Record<string, unknown> | null): number | null {
  const rows = pickPlatformArray(platformOutput ?? undefined, 'iteminfo');
  if (rows.length === 0) {
    return null;
  }
  for (const row of rows) {
    if (!isIteminfoTotalRow(row)) {
      continue;
    }
    const total = Number(row.amt);
    if (Number.isFinite(total)) {
      return Number(total.toFixed(2));
    }
  }
  let sum = 0;
  let any = false;
  for (const row of rows) {
    if (!isIteminfoCategoryRow(row)) {
      continue;
    }
    const amt = Number(row.amt);
    if (!Number.isFinite(amt)) {
      continue;
    }
    sum += amt;
    any = true;
  }
  if (!any) {
    return null;
  }
  return Number(sum.toFixed(2));
}

/**
 * 单元格兜底展示。
 */
export function cellText(value: unknown): string {
  if (value == null || value === '') return '—';
  return String(value);
}

/**
 * 平台子表 ID 取值（规范 etl_* / 吉林 setl_* 并存；始终按字符串展示防精度问题）。
 */
export function pickPlatformRowId(
  row: Record<string, unknown> | null | undefined,
  ...keys: string[]
): string {
  if (!row) {
    return '—';
  }
  for (const key of keys) {
    const raw = row[key];
    if (raw == null || raw === '') {
      continue;
    }
    // 禁止 Number()：长流水号会丢精度
    const text = String(raw).trim();
    if (text) {
      return text;
    }
  }
  return '—';
}

/**
 * 多候选取首个非空文案（忽略 —）。
 */
export function firstDisplay(...values: unknown[]): string {
  for (const value of values) {
    if (value == null || value === '') {
      continue;
    }
    const text = String(value).trim();
    if (!text || text === '—') {
      continue;
    }
    return text;
  }
  return '—';
}
