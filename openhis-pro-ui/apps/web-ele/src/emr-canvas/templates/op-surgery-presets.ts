import {
  ElementType,
  RowFlex,
  TableBorder,
  VerticalAlign,
  type IEditorData,
  type IElement,
} from '@hufe921/canvas-editor';

import {
  DOCUMENT_TITLE_ID,
  TABLE_ROW_HEIGHT_40PT,
  b,
  dateFill,
  fill,
  hospitalTitleEl,
  labeledBox,
  meta,
  nl,
  pageHeader,
  patientBar,
  resetIds,
  t,
  table,
  yesNo,
  type CellSpec,
} from './canvas-kit';

export interface OpSurgeryPreset {
  code: string;
  name: string;
  typeCode: string;
  summary: string;
  data: IEditorData;
}

function pack(title: string, main: IElement[]): IEditorData {
  return {
    header: pageHeader(title),
    main,
    footer: [],
  };
}

function packSafety(main: IElement[]): IEditorData {
  return {
    header: [
      { ...hospitalTitleEl('医院名称'), rowMargin: 1 },
      { value: '\n', rowFlex: RowFlex.CENTER, rowMargin: 1 },
      {
        id: DOCUMENT_TITLE_ID,
        value: '手术安全核查表',
        font: 'SimSun',
        size: 21,
        rowFlex: RowFlex.CENTER,
        rowMargin: 1,
      },
      { type: ElementType.SEPARATOR, value: '', lineWidth: 1 },
    ],
    main,
    footer: [],
  };
}

/** 核查表用单倍行距，避免正文 1.5 倍把单元格撑过固定行高 */
const SSC_ROW = 29;
const SSC_ROW_MARGIN = 1;
const SSC_TABLE_MARGIN = 0.15;

function tight<T extends IElement>(el: T): T {
  return { ...el, rowMargin: SSC_ROW_MARGIN };
}

function emptyCell(): CellSpec {
  return { value: [tight({ value: '' })] };
}

function txt(text: string, span = 1): CellSpec {
  return { value: [tight(t(text))], colspan: span };
}

function head(text: string): CellSpec {
  return { value: [tight(b(text, { rowFlex: RowFlex.CENTER }))], colspan: 2 };
}

function yn(conceptId: string, span = 1): CellSpec {
  return { value: [tight({ ...yesNo(conceptId), rowFlex: RowFlex.CENTER })], colspan: span };
}

function box(conceptId: string, label: string, span = 1): CellSpec {
  return { value: labeledBox(conceptId, label).map((el) => tight(el)), colspan: span };
}

function sign(label: string, conceptId: string): CellSpec {
  return { value: [tight(t(`${label}：`)), tight(fill('签名', conceptId))], colspan: 2 };
}

/** 长文案与是/否同一格，去掉 Word 里多出来的续行 */
function labelYn(label: string, conceptId: string, span = 2): CellSpec {
  return {
    value: [tight(t(label)), tight({ ...yesNo(conceptId), rowFlex: RowFlex.CENTER })],
    colspan: span,
  };
}

function compactTable(colWidths: number[], rows: CellSpec[][], border?: TableBorder): IElement {
  const el = table(colWidths, rows, SSC_ROW);
  el.rowMargin = SSC_TABLE_MARGIN;
  if (border) el.borderType = border;
  for (const row of el.trList ?? []) {
    row.height = SSC_ROW;
    row.minHeight = SSC_ROW;
  }
  return el;
}

/** 对照 Word 六列：每栏 = 标签列 + 是/否列，长文案跨两列 */
function buildSafety(): IEditorData {
  resetIds();
  const grid: CellSpec[][] = [
    [head('麻醉前实施'), head('手术开始前'), head('患者离开手术室前')],
    [
      labelYn('患者姓名、性别、年龄正确：', 'ssc_pre_id'),
      labelYn('患者姓名、性别、年龄正确：', 'ssc_cut_id'),
      labelYn('患者姓名、性别、年龄正确：', 'ssc_leave_id'),
    ],
    [
      txt('手术方式确认：'),
      yn('ssc_pre_method'),
      txt('手术方式确认：'),
      yn('ssc_cut_method'),
      labelYn('实际手术方式确认：', 'ssc_leave_method'),
    ],
    [
      labelYn('手术部位与标识正确：', 'ssc_pre_site'),
      labelYn('手术部位与标识正确：', 'ssc_cut_site'),
      labelYn('手术用药、输血的核查：', 'ssc_leave_drug'),
    ],
    [txt('手术知情同意：'), yn('ssc_pre_op_consent'), emptyCell(), emptyCell(), emptyCell(), emptyCell()],
    [
      txt('麻醉知情同意：'),
      yn('ssc_pre_anes_consent'),
      { value: [tight(b('手术、麻醉风险预警：'))], colspan: 2 },
      labelYn('手术用物清点正确：', 'ssc_leave_count'),
    ],
    [txt('麻醉方式确认：'), yn('ssc_pre_anes_ok'), txt('手术医师陈述：'), emptyCell(), emptyCell(), emptyCell()],
    [
      labelYn('麻醉设备安全检查完成：', 'ssc_pre_equip'),
      box('ssc_cut_time', '预计手术时'),
      box('ssc_cut_blood_loss', '预计失血量'),
      txt('手术标本正确：'),
      yn('ssc_leave_spec'),
    ],
    [
      txt('皮肤是否完整：'),
      yn('ssc_pre_skin'),
      emptyCell(),
      box('ssc_cut_focus', '手术关注点'),
      txt('皮肤是否完整：'),
      yn('ssc_leave_skin'),
    ],
    [
      labelYn('术野皮肤准备正确：', 'ssc_pre_prep'),
      emptyCell(),
      box('ssc_cut_other', '其它'),
      box('ssc_leave_cvc', '中心静脉通路', 2),
    ],
    [txt('麻醉医师陈述：'), emptyCell(), emptyCell(), emptyCell(), box('ssc_leave_pvc', '外周静脉通路', 2)],
    [
      labelYn('静脉通道建立完成：', 'ssc_pre_iv'),
      box('ssc_cut_anes_focus', '麻醉关注点', 2),
      box('ssc_leave_ett', '气管插管', 2),
    ],
    [emptyCell(), emptyCell(), emptyCell(), box('ssc_cut_anes_other', '其它'), box('ssc_leave_ng', '胃管', 2)],
    [
      labelYn('患者是否有过敏史：', 'ssc_pre_allergy'),
      txt('手术护士陈述：'),
      emptyCell(),
      box('ssc_leave_uc', '尿管', 2),
    ],
    [
      emptyCell(),
      emptyCell(),
      box('ssc_cut_sterile', '物品灭菌合格'),
      emptyCell(),
      box('ssc_leave_line_other', '其他', 2),
    ],
    [
      labelYn('抗菌药物皮试结果：', 'ssc_pre_skin_test'),
      box('ssc_cut_device', '仪器设备'),
      emptyCell(),
      txt('患者去向：'),
      box('ssc_dest_leave', '离院'),
    ],
    [
      txt('术前备血：'),
      yn('ssc_pre_blood'),
      box('ssc_cut_meds', '术前术中特殊用药情况'),
      box('ssc_cut_nurse_other', '其它'),
      emptyCell(),
      box('ssc_dest_pacu', '恢复室'),
    ],
    [
      {
        value: [
          ...labeledBox('ssc_pre_prosthesis', '假体').map((el) => tight(el)),
          tight(t('  ')),
          ...labeledBox('ssc_pre_implant', '体内植入物').map((el) => tight(el)),
          tight(t('  ')),
          ...labeledBox('ssc_pre_imaging', '影像学资料').map((el) => tight(el)),
        ],
        colspan: 2,
      },
      txt('是否需要相关影像资料：'),
      yn('ssc_cut_need_img'),
      emptyCell(),
      box('ssc_dest_ward', '病房'),
    ],
    [
      txt('输血知情同意：'),
      yn('ssc_pre_trans_consent'),
      emptyCell(),
      emptyCell(),
      emptyCell(),
      box('ssc_dest_icu', 'ICU'),
    ],
    [
      txt('其他：'),
      { value: [tight(fill('其他', 'ssc_pre_other'))] },
      txt('其他：'),
      { value: [tight(fill('其他', 'ssc_cut_other_note'))] },
      txt('其他：'),
      box('ssc_dest_er', '急诊'),
    ],
    [
      sign('手术医师签名', 'ssc_pre_op_sign'),
      sign('手术医师签名', 'ssc_cut_op_sign'),
      sign('手术医师签名', 'ssc_leave_op_sign'),
    ],
    [
      sign('麻醉医师签名', 'ssc_pre_anes_sign'),
      sign('麻醉医师签名', 'ssc_cut_anes_sign'),
      sign('麻醉医师签名', 'ssc_leave_anes_sign'),
    ],
    [
      sign('手术室护士签名', 'ssc_pre_nurse_sign'),
      sign('手术室护士签名', 'ssc_cut_nurse_sign'),
      sign('手术室护士签名', 'ssc_leave_nurse_sign'),
    ],
  ];

  const gridTable = compactTable([126, 108, 110, 117, 115, 122], grid);
  gridTable.tableToolDisabled = true;

  return packSafety([
    compactTable(
      [140, 140, 130, 140, 148],
      [
        [
          { value: [tight(t('姓名：')), tight(meta('patientName', '患者姓名'))] },
          { value: [tight(t('科室：')), tight(meta('deptName', '科室'))] },
          { value: [tight(t('性别：')), tight(meta('gender', '性别'))] },
          { value: [tight(t('年龄：')), tight(meta('age', '年龄')), tight(t(' 岁'))] },
          { value: [tight(t('门诊号：')), tight(meta('medicalRecordNo', '病历号'))] },
        ],
      ],
      TableBorder.EMPTY,
    ),
    compactTable(
      [174, 175, 174, 175],
      [
        [
          { value: [tight(t('麻醉方式：')), tight(fill('麻醉方式', 'anesthesiaMethod'))] },
          { value: [tight(t('手术者：')), tight(fill('手术者', 'surgeon'))] },
          { value: [tight(t('手术方式：')), tight(fill('手术方式', 'surgeryMethod'))] },
          { value: [tight(t('手术日期：')), tight(dateFill('surgeryDate', '手术日期'))] },
        ],
      ],
      TableBorder.EMPTY,
    ),
    gridTable,
  ]);
}

function buildConsent(): IEditorData {
  resetIds();
  return pack('手术知情同意书', [
    patientBar(),
    nl(),
    b('术前诊断：'),
    fill('术前诊断', 'preopDiagnosis'),
    nl(),
    b('拟行手术名称：'),
    fill('拟行手术名称', 'plannedSurgeryName'),
    nl(),
    b('医生陈述：'),
    nl(),
    t(
      '我已经告知患者将要进行的手术的适应症、禁忌症，手术前准备、拟采取的麻醉方式、此次手术及手术后可能发生的并发症及合并症、可能存在的其他手术方法及备选的替代治疗方案，并且充分解答了患者关于此次手术的相关问题。',
    ),
    nl(),
    t('医生签名：'),
    meta('doctorName', '接诊医生'),
    t('　　签名日期：'),
    dateFill('doctorSignDate', '医生签字日期'),
    nl(),
    b('患者或患者家属陈述：'),
    nl(),
    t(
      '1.医生已经告知我将要进行的手术的适应症、禁忌症，手术前准备、拟采取的麻醉方式、此次手术及手术后可能发生的并发症及合并症、可能存在的其他手术方法及备选的替代治疗方案，并且充分解答了患者关于此次手术的相关问题。',
    ),
    nl(),
    t('2.我同意并接受在手术中医生可以根据我的病情对预定的手术方式做出调整。'),
    nl(),
    t('3.我理解我的手术需要多位医生共同参与。'),
    nl(),
    t('4.医生并未向我做出手术百分百成功的许诺。'),
    nl(),
    t('5.我授权医生对手术切除的病变器官、组织或标本进行处置，包括病理学检查、细胞学检查和医疗废物处理等。'),
    nl(),
    t('6.其他：'),
    fill('知情同意其他说明', 'consentOther', undefined, '无'),
    nl(),
    b('患者或患者家属意见：'),
    t('（请患者/患者家属确认后逐字抄写下面内容）'),
    nl(),
    t('“我对以上情况已充分知情了解。”'),
    nl(),
    fill('患者或患者家属意见抄写', 'consentCopy'),
    nl(),
    t('患者/被授权人签名：'),
    fill('患者或被授权人签名', 'patientSign'),
    t('　　与患者关系：'),
    fill('与患者的关系', 'relationToPatient'),
    t('　　签名日期：'),
    dateFill('patientSignDate', '患者签字日期'),
  ]);
}

function buildRecord(): IEditorData {
  resetIds();
  return pack('手术记录', [
    patientBar(),
    nl(),
    b('手术开始时间：'),
    dateFill('surgeryStartTime', '手术开始时间'),
    nl(),
    b('手术结束时间：'),
    dateFill('surgeryEndTime', '手术结束时间'),
    nl(),
    b('拟行手术名称：'),
    fill('拟行手术名称', 'plannedSurgeryName'),
    nl(),
    b('实施手术名称：'),
    fill('实施手术名称', 'actualSurgeryName'),
    nl(),
    b('术前诊断：'),
    fill('术前诊断', 'preopDiagnosis'),
    nl(),
    b('术中诊断：'),
    fill('术中诊断', 'intraopDiagnosis'),
    nl(),
    b('病理送检：'),
    yesNo('pathology_sent'),
    nl(),
    b('麻醉方式：'),
    fill('麻醉方式', 'anesthesiaMethod'),
    t('　　'),
    b('麻醉者：'),
    fill('麻醉者', 'anesthetist'),
    nl(),
    b('术者：'),
    fill('术者', 'surgeon'),
    t('　　'),
    b('助手：'),
    fill('助手', 'assistant'),
    nl(),
    b('洗手护士：'),
    fill('洗手护士', 'scrubNurse'),
    t('　　'),
    b('巡回护士：'),
    fill('巡回护士', 'circulatingNurse'),
    nl(),
    b('手术经过、术中出现的情况及处理：'),
    nl(),
    fill('手术经过、术中出现的情况及处理', 'operativeCourse'),
    nl(),
    t('记录者(术者或一助)签名：'),
    fill('记录者签名', 'recorderSign'),
    t('　　术者签名：'),
    fill('术者签名', 'surgeonSign'),
  ]);
}

const RISK_COLS = [232, 233, 233] as const;

function riskTable(colWidths: number[], rows: CellSpec[][]): IElement {
  const el = table(colWidths, rows, TABLE_ROW_HEIGHT_40PT);
  el.tableToolDisabled = true;
  return el;
}

/** 分值写在选项文字后，不再套内嵌表，避免分值列压到旁边一栏。 */
function riskOpt(conceptId: string, label: string, score: number): IElement[] {
  return [...labeledBox(conceptId, `${label} ${score}`), nl()];
}

function riskHead(text: string): CellSpec {
  return { value: [b(text, { rowFlex: RowFlex.CENTER })] };
}

function riskSign(label: string, conceptId: string): CellSpec {
  return { value: [t(`${label}：`), fill('签名', conceptId)] };
}

function buildRisk(): IEditorData {
  resetIds();

  const incision: IElement[] = [
    ...riskOpt('incision_1', 'Ⅰ类手术切口(清洁手术)', 0),
    t('手术野无污染；手术切口周边无炎症；患者没有进行气道、食道/或尿道插管；患者没有意识障碍'),
    nl(),
    ...riskOpt('incision_2', 'Ⅱ类手术切口(相对清洁手术)', 0),
    t(
      '上、下呼吸道，上、下消化道，泌尿生殖道或经以上器官的手术；患者进行气道、食道/或尿道插管；患者病情稳定；行胆囊、阴道、阑尾、耳鼻手术的患者。',
    ),
    nl(),
    ...riskOpt('incision_3', 'Ⅲ类手术切口(清洁-污染手术)', 1),
    t('开放，新鲜且不干净的伤口；前次手术后感染的切口；手术中需采取消毒措施的切口'),
    nl(),
    ...riskOpt('incision_4', 'Ⅳ类切口(污染手术)', 1),
    t('严重的外伤，手术切口有炎症、组织坏死，或有内脏引流管。'),
  ];

  const asa: IElement[] = [
    ...riskOpt('asa_p1', 'P1：正常的患者：除局部病变外，无系统性疾病', 0),
    ...riskOpt('asa_p2', 'P2：患者有轻微的临床症状；有轻度或中度系统性疾病', 0),
    ...riskOpt('asa_p3', 'P3：有严重系统性疾病，日常活动受限，但未丧失工作能力', 1),
    ...riskOpt('asa_p4', 'P4：有严重系统性疾病，已丧失工作能力，威胁生命安全', 1),
    ...riskOpt('asa_p5', 'P5：病情危重，生命难以维持的濒死病人', 1),
    ...riskOpt('asa_p6', 'P6：脑死亡的患者', 1),
    b('4.手术类别'),
    nl(),
    ...labeledBox('opcat_superficial', '1.浅层组织手术'),
    nl(),
    ...labeledBox('opcat_deep', '2.深部组织手术'),
    nl(),
    ...labeledBox('opcat_organ', '3.器官手术'),
    nl(),
    ...labeledBox('opcat_cavity', '4.腔隙手术'),
    nl(),
    ...labeledBox('opcat_emergency', '5.急诊手术'),
  ];

  const duration: IElement[] = [
    ...riskOpt('duration_t1', 'T1：手术在3小时内完成', 0),
    ...riskOpt('duration_t2', 'T2：完成手术，超过3小时', 1),
    t('巡回护士签名：'),
    fill('签名', 'risk_nurse_sign'),
    nl(),
    b('随访：切口愈合与感染情况'),
    nl(),
    ...labeledBox('fu_a', '切口甲级愈合'),
    nl(),
    ...labeledBox('fu_b', '切口感染--浅层感染'),
    nl(),
    ...labeledBox('fu_c', '深层感染'),
  ];

  const top: CellSpec = { verticalAlign: VerticalAlign.TOP };
  return pack('手术风险评估表', [
    riskTable(
      [160, 120, 150, 268],
      [
        [
          { value: [b('姓名：'), meta('patientName', '患者姓名')] },
          { value: [b('性别：'), meta('gender', '性别')] },
          { value: [b('年龄：'), meta('age', '年龄'), t(' 岁')] },
          { value: [b('门诊号：'), meta('medicalRecordNo', '病历号')] },
        ],
      ],
    ),
    riskTable(
      [180, 278, 240],
      [
        [
          { value: [b('科室：'), meta('deptName', '科室')] },
          { value: [b('实施手术名称：'), fill('实施手术名称', 'actualSurgeryName')] },
          { value: [b('评估时间：'), dateFill('recordTime', '记录时间')] },
        ],
      ],
    ),
    riskTable([...RISK_COLS], [
      [
        riskHead('1.手术切口清洁程度'),
        riskHead('2.麻醉分级(ASA分级)'),
        riskHead('3.手术持续时间'),
      ],
      [
        { ...top, value: incision },
        { ...top, value: asa },
        { ...top, value: duration },
      ],
      [
        riskSign('手术医生签名', 'risk_surgeon_sign'),
        riskSign('麻醉医师签名', 'risk_anes_sign'),
        riskSign('经治医师签名', 'risk_attending_sign'),
      ],
    ]),
    nl(),
    t('手术风险评估：手术切口清洁程度（'),
    fill('切口分', 'nnis_incision_score', 36),
    t('分）+麻醉ASA分级（'),
    fill('ASA分', 'nnis_asa_score', 36),
    t('分）+手术持续时间（'),
    fill('时间分', 'nnis_time_score', 36),
    t('分）= '),
    fill('总分', 'nnis_total', 40),
    t('分'),
    nl(),
    t('NNIS分级：'),
    ...labeledBox('nnis_0', '0'),
    t('　'),
    ...labeledBox('nnis_1', '1'),
    t('　'),
    ...labeledBox('nnis_2', '2'),
    t('　'),
    ...labeledBox('nnis_3', '3'),
    nl(),
    t('在与评价项目相应的框内打钩后，分值相加即可完成。'),
  ]);
}

export function getOpSurgeryPresets(): OpSurgeryPreset[] {
  return [
    {
      code: 'OP_SURGERY_SAFETY_CHECK',
      name: '手术安全核查表',
      typeCode: 'SURGERY_SAFETY_CHECK',
      summary: '麻醉前 / 开始前 / 离室前三栏核查',
      data: buildSafety(),
    },
    {
      code: 'OP_SURGERY_CONSENT',
      name: '手术知情同意书',
      typeCode: 'SURGERY_CONSENT',
      summary: '医生陈述、家属抄写与签字',
      data: buildConsent(),
    },
    {
      code: 'OP_SURGERY_RECORD',
      name: '手术记录',
      typeCode: 'SURGERY_RECORD',
      summary: '起止时间、诊断、经过与签名',
      data: buildRecord(),
    },
    {
      code: 'OP_SURGERY_RISK',
      name: '手术风险评估表',
      typeCode: 'SURGERY_RISK_ASSESS',
      summary: '切口清洁度 / ASA / 手术时长 NNIS',
      data: buildRisk(),
    },
  ];
}

export function getOpSurgeryPreset(code: string): OpSurgeryPreset | undefined {
  return getOpSurgeryPresets().find((item) => item.code === code);
}
