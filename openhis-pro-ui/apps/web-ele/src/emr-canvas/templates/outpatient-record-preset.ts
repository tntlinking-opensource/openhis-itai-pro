import { VerticalAlign, type IEditorData, type IElement } from '@hufe921/canvas-editor';

import {
  TABLE_ROW_HEIGHT_40PT,
  b,
  fill,
  meta,
  nl,
  pageHeader,
  resetIds,
  t,
  table,
  type CellSpec,
} from './canvas-kit';
import type { OpSurgeryPreset } from './op-surgery-presets';

/** A4 内容宽：794 − 左右各 1.27cm（48px） */
const PAGE_INNER = 698;
const PATIENT_COL = 232;
const LABEL_COL = 90;
const VALUE_COL = PAGE_INNER - LABEL_COL;
const EXAM_COL = Math.floor(VALUE_COL / 2);

function pack(title: string, main: IElement[]): IEditorData {
  return {
    header: pageHeader(title),
    main,
    footer: [],
  };
}

function kv(label: string, control: IElement, suffix?: string): IElement[] {
  const items = [b(`${label}：`), control];
  if (suffix) items.push(t(` ${suffix}`));
  return items;
}

function labelCell(text: string): CellSpec {
  return {
    value: [b(text)],
    verticalAlign: VerticalAlign.MIDDLE,
  };
}

function valueCell(value: IElement[]): CellSpec {
  return {
    value,
    verticalAlign: VerticalAlign.MIDDLE,
  };
}

function noneFill(label: string, conceptId: string): IElement {
  return fill(label, conceptId, undefined, '无');
}

/** 主诉等到处理意见占满右两列。体温四格单独成格，不放进内嵌表。 */
function spanValue(value: IElement[]): CellSpec {
  return {
    value,
    colspan: 2,
    verticalAlign: VerticalAlign.MIDDLE,
  };
}

function patientInfoTable(): IElement {
  return table(
    [PATIENT_COL, PATIENT_COL, PAGE_INNER - PATIENT_COL * 2],
    [
      [
        { value: kv('姓名', meta('patientName', '患者姓名')) },
        { value: kv('性别', meta('gender', '性别')) },
        { value: kv('年龄', meta('age', '年龄'), '岁') },
      ],
      [
        { value: kv('医保', meta('insuranceName', '医保')) },
        { value: kv('电话', meta('phone', '联系电话')) },
        { value: kv('病历号', meta('medicalRecordNo', '病历号')) },
      ],
      [
        { value: kv('就诊日期', meta('visitTime', '就诊日期')) },
        { value: kv('就诊科室', meta('deptName', '科室')) },
        { value: kv('接诊医生', meta('doctorName', '接诊医生', true)) },
      ],
    ],
  );
}

function clinicalTable(): IElement {
  const h = TABLE_ROW_HEIGHT_40PT;
  const rows: CellSpec[][] = [
    [labelCell('主诉'), spanValue([fill('主诉', 'chiefComplaint')])],
    [labelCell('现病史'), spanValue([fill('现病史', 'presentHistory')])],
    [labelCell('既往史'), spanValue([noneFill('既往史', 'pastHistory')])],
    [labelCell('过敏史'), spanValue([noneFill('过敏史', 'allergyHistory')])],
    [labelCell('家族史'), spanValue([noneFill('家族史', 'familyHistory')])],
    [labelCell('个人史'), spanValue([noneFill('个人史', 'personalHistory')])],
    [
      { value: [b('体格检查')], verticalAlign: VerticalAlign.MIDDLE, rowspan: 3 },
      valueCell(kv('体温', fill('体温', 'temperature'), '℃')),
      valueCell(kv('脉搏', fill('脉搏', 'pulse'), '次/分')),
    ],
    [
      valueCell(kv('呼吸', fill('呼吸', 'respiration'), '次/分')),
      valueCell(kv('血压', fill('血压', 'bloodPressure'), 'mmHg')),
    ],
    [spanValue([noneFill('体格检查', 'physicalExam')])],
    [labelCell('辅助检查'), spanValue([noneFill('辅助检查', 'auxiliaryExam')])],
    [labelCell('诊断'), spanValue([fill('诊断', 'westernDiagnosis')])],
    [labelCell('处理意见'), spanValue([fill('处理意见', 'treatmentPlan')])],
  ];
  return table(
    [LABEL_COL, EXAM_COL, VALUE_COL - EXAM_COL],
    rows,
    [h, h * 2, h, h, h, h, h, h, h * 2, h, h, 80],
  );
}

/** 院名页眉 + 患者三列表 + 主诉至处理意见大表 */
export function buildOutpatientRecord(title = '门诊病历'): IEditorData {
  resetIds();
  return pack(title, [patientInfoTable(), nl(), clinicalTable()]);
}

export function getOutpatientTemplatePresets(): OpSurgeryPreset[] {
  return [
    {
      code: 'OP_OUTPATIENT_RECORD',
      name: '门诊病历',
      typeCode: 'OUTPATIENT_RECORD',
      summary: '患者栏 + 主诉至处理意见',
      data: buildOutpatientRecord('门诊病历'),
    },
    {
      code: 'OP_OUTPATIENT_FIRST_VISIT',
      name: '门诊病历（初诊）',
      typeCode: 'OUTPATIENT_RECORD',
      summary: '初诊套用，版式同门诊病历',
      data: buildOutpatientRecord('门诊病历（初诊）'),
    },
    {
      code: 'OP_OUTPATIENT_FOLLOW_UP',
      name: '门诊病历（复诊）',
      typeCode: 'OUTPATIENT_RECORD',
      summary: '复诊套用，版式同门诊病历',
      data: buildOutpatientRecord('门诊病历（复诊）'),
    },
  ];
}
