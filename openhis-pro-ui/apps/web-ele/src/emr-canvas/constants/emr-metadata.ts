/** 可绑定系统字段（conceptId 与模版 meta() 对齐） */
export interface EmrMetadataField {
  fieldKey: string;
  label: string;
  group: '患者' | '就诊' | '医护';
  /** false = 只读槽，随患者信息刷新 */
  editable?: boolean;
}

/** 侧栏可插入的元数据目录 */
export const EMR_METADATA_FIELDS: EmrMetadataField[] = [
  { fieldKey: 'patientName', label: '患者姓名', group: '患者', editable: false },
  { fieldKey: 'gender', label: '性别', group: '患者', editable: false },
  { fieldKey: 'age', label: '年龄', group: '患者', editable: false },
  { fieldKey: 'idCard', label: '身份证号', group: '患者', editable: false },
  { fieldKey: 'phone', label: '联系电话', group: '患者', editable: false },
  { fieldKey: 'insuranceName', label: '医保', group: '就诊', editable: false },
  { fieldKey: 'medicalRecordNo', label: '病历号', group: '就诊', editable: false },
  { fieldKey: 'deptName', label: '科室', group: '就诊', editable: false },
  { fieldKey: 'wardName', label: '病房', group: '就诊', editable: false },
  { fieldKey: 'bedNo', label: '床号', group: '就诊', editable: false },
  { fieldKey: 'visitTime', label: '就诊时间', group: '就诊', editable: false },
  { fieldKey: 'doctorName', label: '接诊医生', group: '医护', editable: true },
  { fieldKey: 'hospitalName', label: '医院名称', group: '医护', editable: false },
];

/** 实验室演示患者（联调前本地带入） */
export const DEMO_PATIENT_METADATA: Record<string, string> = {
  patientName: '张三',
  gender: '男',
  age: '45',
  idCard: '110101198001011234',
  phone: '13800138000',
  medicalRecordNo: 'MZ20261005001',
  deptName: '骨科门诊',
  wardName: '',
  bedNo: '',
  visitTime: '2026-10-05 09:30',
  doctorName: '李医生',
  hospitalName: '示例医院',
  insuranceName: '市医保',
};
