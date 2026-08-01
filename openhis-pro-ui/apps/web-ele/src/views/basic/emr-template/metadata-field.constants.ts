/** 与后端 MetadataValueSource 枚举对齐 */
export const METADATA_VALUE_SOURCE_OPTIONS: { label: string; value: string }[] = [
  { label: '患者主档', value: 'PATIENT' },
  { label: '就诊', value: 'ENCOUNTER' },
  { label: '入院', value: 'ADMISSION' },
  { label: '医保', value: 'INSURANCE' },
  { label: '机构', value: 'ORG' },
  { label: '科室', value: 'DEPT' },
  { label: '病区', value: 'WARD' },
  { label: '医务人员', value: 'PRACTITIONER' },
  { label: '当前用户', value: 'USER' },
  { label: '用户科室', value: 'USER_DEPT' },
  { label: '文书', value: 'DOCUMENT' },
  { label: '模板', value: 'TEMPLATE' },
  { label: '版本', value: 'VERSION' },
  { label: '诊断摘要', value: 'DIAGNOSIS' },
  { label: '过敏摘要', value: 'ALLERGY' },
  { label: '体征摘要', value: 'VITAL_SIGN' },
  { label: '检验摘要', value: 'LAB_SUMMARY' },
  { label: '检查摘要', value: 'EXAM_SUMMARY' },
  { label: '字典', value: 'DICT' },
  { label: '读模型', value: 'READ_MODEL' },
  { label: '系统配置', value: 'SETTING' },
  { label: '系统运行时', value: 'SYSTEM' },
  { label: '常量', value: 'CONST' },
  { label: '表达式', value: 'EXPR' },
  { label: '引用字段', value: 'FIELD_REF' },
  { label: '外部接口', value: 'HTTP_API' },
  { label: '请求覆盖', value: 'REQUEST' },
  { label: '手工', value: 'MANUAL' },
];

export const METADATA_DATA_TYPE_OPTIONS = [
  { label: '文本', value: 'string' },
  { label: '数值', value: 'number' },
  { label: '日期', value: 'date' },
  { label: '日期时间', value: 'datetime' },
];

export const METADATA_MASK_RULE_OPTIONS = [
  { label: '无', value: 'NONE' },
  { label: '证件号', value: 'ID_CARD' },
  { label: '电话', value: 'PHONE' },
];

export function formatMetadataValueSource(code?: string) {
  if (!code) return '—';
  return METADATA_VALUE_SOURCE_OPTIONS.find((o) => o.value === code)?.label ?? code;
}

/** 维护弹窗顶部说明（元数据 vs 数据项名） */
export const METADATA_FIELD_FORM_INTRO = [
  '元数据字段用于页眉患者条、模板标题区等占位展示，通过 fieldKey 绑定编辑器 chip。',
  '与文书结构化「数据项名」（attrs.name，如主诉、现病史）不是同一套标识，请勿混用 fieldKey 与数据项名。',
  '打开文书时，后端按 patientId / encounterId / documentId 解析各字段取值；解析失败时使用默认值，不阻断打开。',
].join('\n');

/** 各表单项下方的填写说明 */
export const METADATA_FIELD_FORM_FIELD_HINTS = {
  fieldKey:
    '模板与文书 chip 绑定的机器名，租户内唯一。保存后不宜修改；建议 camelCase，如 patientName、primaryDiagnosis。',
  label: '维护页与插入 chip 时展示的汉字名称，如「患者姓名」「主诊断」。',
  dataType: '影响展示与校验；日期类可配合「格式」字段；多数页眉项用文本即可。',
  valueSource:
    '决定从哪类运行时上下文取值。先选来源，再在 source_path 中选或输入点分路径；路径须与后端已实现的键一致。',
  sourceConfig:
    'JSON 配置：EXPR 写表达式，FIELD_REF 写 refFieldKey，DICT/READ_MODEL 等写扩展参数。留空表示不使用。',
  defaultValue: '解析为空或失败时的兜底展示；CONST / MANUAL 来源时作为主要取值。',
  editableDefault:
    'true：插入后允许在正文内改展示文本（仅保存在文书 content，不回写患者主档）。false：每次刷新用最新解析值覆盖。',
  maskRule: '列表、打印、日志展示时的脱敏规则；证件号、电话按规则打码。',
  formatPattern: '可选；日期/日期时间类 fieldKey 的展示格式（如 yyyy-MM-dd）。',
  sortOrder: '维护列表与「插入元数据」下拉的排序，数字越小越靠前。',
  isEnabled: '停用后不可在新模板中插入；已引用节点保留 fieldKey，取值可能为空。',
  remark: '维护备注，不影响解析逻辑。',
} as const;

/** 按 value_source 补充说明（选中后显示在取值来源下方） */
export const METADATA_VALUE_SOURCE_HELP: Record<string, string> = {
  PATIENT: '需要 patientId。从患者主档解析，如 patient.name、patient.idCard。',
  ENCOUNTER: '需要 encounterId。从当前就诊解析，如流水号、科室（科室名当前可能为组织 ID 占位）。',
  ADMISSION:
    '住院入院信息；分床/清床日期请用 ENCOUNTER 的 encounter.bedAssignedAt / encounter.bedClearedAt。',
  INSURANCE:
    '医保/费别，常用 insurance.payorName（优先就诊 ybClassText，空则取优先合同名，与侧栏费用性质同口径）。',
  ORG: '机构/院区；自就诊科室向上回溯医院节点，写入 org.name（机构名称）。',
  DEPT: '科室展示，可用 dept.name 或 encounter.deptName（与就诊科室同源）。',
  WARD: '病区；后端尚未贡献，待入院读模型对接。',
  PRACTITIONER: '医务人员主档；当前登录用户写入 practitioner.name、practitioner.signatureImage。',
  USER: '当前登录用户，如 user.displayName、user.signatureImage（医生签名图，来自 adm_practitioner.signature）。',
  USER_DEPT: '当前用户所属科室，如 user.deptName。',
  DOCUMENT: '需要已打开文书 documentId，如 document.title、document.documentTypeName。',
  TEMPLATE: '所用模板名称；后端尚未贡献。',
  VERSION: '版本/留痕号；P2+ 规划。',
  DIAGNOSIS:
    '需要 encounterId。从 adm_encounter_diagnosis 组装快照：diagnosis.primaryDisplay（主诊）、diagnosis.summary（含[主]标签摘要）、diagnosis.dischargeDisplay（出院诊断书用，去标签、分号拼接）。',
  ALLERGY: '过敏摘要；后端尚未贡献。',
  VITAL_SIGN: '体征摘要；P2+ 规划。',
  LAB_SUMMARY: '检验摘要；P2+ 规划。',
  EXAM_SUMMARY: '检查摘要；P2+ 规划。',
  DICT: '先取 code 再译 label；配置写在 source_config，非 source_path。',
  READ_MODEL: '指定读模型查询；配置写在 source_config。',
  SETTING: '系统/EMR 配置项；配置写在 source_config。',
  SYSTEM: '服务器时间，如 system.now、system.printedAt。',
  CONST: '固定常量，无需 source_path，填写「默认值」即可。门诊戒烟页脚 smokeFreeFooter 用此来源。',
  EXPR: '用 source_config.expr 拼接已解析字段，如 {"expr":"{patient.name} {patient.age}岁"}。',
  FIELD_REF: '引用同次解析中另一 fieldKey，source_config 示例：{"refFieldKey":"deptName"}。',
  HTTP_API: '外部 HTTP 适配；配置写在 source_config。',
  REQUEST: '仅当接口请求体 metadataOverrides 含该 fieldKey 时生效，优先级最高。',
  MANUAL: '不自动取值，依赖默认值或用户在 chip 上手工输入。门诊病历主诉/病史等临床段用此来源。',
};

export function getMetadataValueSourceHelp(valueSource: string): string {
  if (!valueSource) return METADATA_FIELD_FORM_FIELD_HINTS.valueSource;
  return (
    METADATA_VALUE_SOURCE_HELP[valueSource.trim().toUpperCase()] ??
    '请查阅《元数据字段定义与维护》§3.3，或联系研发确认该来源是否已实现。'
  );
}

/** source_path 下拉项（与后端 EmrMetadataContextBuilder / HealthEmrMetadataContextAdapter 已实现键对齐） */
export interface MetadataSourcePathOption {
  value: string;
  /** 下拉展示：路径 + 说明 */
  label: string;
}

/**
 * 按 value_source 预置可选 source_path。
 * 未列出的来源表示后端尚未贡献对应键，维护时可手动输入（allow-create）。
 */
export const METADATA_SOURCE_PATH_BY_VALUE_SOURCE: Record<string, MetadataSourcePathOption[]> = {
  PATIENT: [
    { value: 'patient.id', label: 'patient.id — 患者 ID' },
    { value: 'patient.name', label: 'patient.name — 姓名' },
    { value: 'patient.gender', label: 'patient.gender — 性别 code' },
    { value: 'patient.age', label: 'patient.age — 年龄（岁）' },
    { value: 'patient.birthDate', label: 'patient.birthDate — 出生日期' },
    { value: 'patient.medicalRecordNo', label: 'patient.medicalRecordNo — 病历号' },
    { value: 'patient.idCard', label: 'patient.idCard — 证件号' },
    { value: 'patient.phone', label: 'patient.phone — 电话' },
    { value: 'patient.workCompany', label: 'patient.workCompany — 工作单位' },
  ],
  ENCOUNTER: [
    { value: 'encounter.id', label: 'encounter.id — 就诊 ID' },
    { value: 'encounter.busNo', label: 'encounter.busNo — 就诊流水号/住院号' },
    { value: 'encounter.organizationId', label: 'encounter.organizationId — 组织 ID' },
    {
      value: 'encounter.deptName',
      label: 'encounter.deptName — 科室（当前为组织 ID 占位）',
    },
    { value: 'encounter.startTime', label: 'encounter.startTime — 就诊开始时间（含时分）' },
    {
      value: 'encounter.startDate',
      label: 'encounter.startDate — 就诊日期（yyyy-MM-dd，门诊病历用）',
    },
    { value: 'encounter.admissionTime', label: 'encounter.admissionTime — 入院/开始时间' },
    {
      value: 'encounter.bedAssignedAt',
      label: 'encounter.bedAssignedAt — 首次分床日期（yyyy-MM-dd）',
    },
    {
      value: 'encounter.bedClearedAt',
      label: 'encounter.bedClearedAt — 清床日期（yyyy-MM-dd）',
    },
    {
      value: 'encounter.lengthOfStayDays',
      label: 'encounter.lengthOfStayDays — 住院天数（分床→清床）',
    },
    { value: 'encounter.classEnum', label: 'encounter.classEnum — 类别 AMB/IMP' },
  ],
  ADMISSION: [],
  INSURANCE: [{ value: 'insurance.payorName', label: 'insurance.payorName — 医保/费别' }],
  ORG: [{ value: 'org.name', label: 'org.name — 医院名称（自科室向上回溯 HOSPITAL）' }],
  DEPT: [
    { value: 'dept.name', label: 'dept.name — 科室名称' },
    {
      value: 'encounter.deptName',
      label: 'encounter.deptName — 就诊科室名称',
    },
  ],
  WARD: [],
  PRACTITIONER: [
    { value: 'practitioner.id', label: 'practitioner.id — 医务人员 ID' },
    { value: 'practitioner.name', label: 'practitioner.name — 姓名' },
    { value: 'practitioner.busNo', label: 'practitioner.busNo — 工号' },
    {
      value: 'practitioner.signatureImage',
      label: 'practitioner.signatureImage — 签名图（data URL）',
    },
  ],
  USER: [
    { value: 'user.id', label: 'user.id — 用户 ID' },
    { value: 'user.displayName', label: 'user.displayName — 显示名' },
    { value: 'user.practitionerId', label: 'user.practitionerId — 医务人员 ID' },
    {
      value: 'user.signatureImage',
      label: 'user.signatureImage — 医生签名图（adm_practitioner.signature）',
    },
  ],
  USER_DEPT: [
    { value: 'user.deptId', label: 'user.deptId — 科室 ID' },
    { value: 'user.deptName', label: 'user.deptName — 科室名（当前为 ID 占位）' },
  ],
  DOCUMENT: [
    { value: 'document.id', label: 'document.id — 文书 ID' },
    { value: 'document.title', label: 'document.title — 标题' },
    { value: 'document.documentTypeCode', label: 'document.documentTypeCode — 类型编码' },
    { value: 'document.documentTypeName', label: 'document.documentTypeName — 类型名称' },
    { value: 'document.status', label: 'document.status — 状态' },
    { value: 'document.createdAt', label: 'document.createdAt — 创建时间' },
  ],
  TEMPLATE: [],
  VERSION: [],
  DIAGNOSIS: [
    { value: 'diagnosis.primaryDisplay', label: 'diagnosis.primaryDisplay — 主诊断展示' },
    { value: 'diagnosis.summary', label: 'diagnosis.summary — 诊断摘要' },
    {
      value: 'diagnosis.dischargeDisplay',
      label: 'diagnosis.dischargeDisplay — 出院诊断（全量，分号分隔）',
    },
    {
      value: 'diagnosis.westernDisplay',
      label: 'diagnosis.westernDisplay — 西医诊断（分号分隔）',
    },
    {
      value: 'diagnosis.tcmDisplay',
      label: 'diagnosis.tcmDisplay — 中医诊断（病证合并，分号分隔）',
    },
    {
      value: 'diagnosis.primaryConditionDefinitionId',
      label: 'diagnosis.primaryConditionDefinitionId — 主诊断目录 ID',
    },
  ],
  ALLERGY: [],
  VITAL_SIGN: [],
  LAB_SUMMARY: [],
  EXAM_SUMMARY: [],
  SYSTEM: [
    { value: 'system.now', label: 'system.now — 当前日期时间' },
    { value: 'system.today', label: 'system.today — 当天日期' },
    { value: 'system.printedAt', label: 'system.printedAt — 打印时间' },
  ],
};

export function getMetadataSourcePathOptions(valueSource: string): MetadataSourcePathOption[] {
  if (!valueSource) {
    return [];
  }
  return METADATA_SOURCE_PATH_BY_VALUE_SOURCE[valueSource.trim().toUpperCase()] ?? [];
}

export function findMetadataSourcePathOption(
  valueSource: string,
  sourcePath: string,
): MetadataSourcePathOption | undefined {
  const path = sourcePath.trim();
  if (!path) {
    return undefined;
  }
  return getMetadataSourcePathOptions(valueSource).find((o) => o.value === path);
}
