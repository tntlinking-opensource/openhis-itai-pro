<script lang="ts" setup>
import type {
  CreatePatientParams,
  EditPatientParams,
  Patient,
  PatientIdInfo,
} from '#/api/basic/patient';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import dayjs from 'dayjs';
import {
  ElButton,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTabPane,
  ElTabs,
  ElTable,
  ElTableColumn,
} from 'element-plus';
import countryList from 'province-city-china/dist/country.json';

import { useVbenForm, z } from '#/adapter/form';
import { createPatientApi, editPatientApi } from '#/api/basic/patient';
import { loadArchiveApi, saveArchiveApi } from '#/api/peis/health-archive';
import SelectArea from '#/components/select-area.vue';
import { isAcceptableChineseIdCardInput, parseChineseIdCard18 } from '#/utils/format/id-card-cn';

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  certTypeOptions: () => [],
  maritalStatusOptions: () => [],
  bloodTypeAboOptions: () => [],
  bloodTypeRhOptions: () => [],
  sexOptions: () => [],
  educationLevelOptions: () => [],
  formData: null,
});

const emit = defineEmits<{
  /** 新建成功时带出 Patient；编辑成功时无参 */
  success: [patient?: Patient];
  'update:visible': [value: boolean];
}>();

/** 标识用途选项：1常用 2正式 3临时 4次要 5旧（与后端 IdentifierUse 一致） */
const USE_ENUM_OPTIONS = [
  { label: '常用', value: 1 },
  { label: '正式', value: 2 },
  { label: '临时', value: 3 },
  { label: '次要', value: 4 },
  { label: '旧', value: 5 },
] as const;

interface Props {
  visible?: boolean;
  /** 证件类型下拉（来自 init 的 identityDocumentType，已排除身份证） */
  certTypeOptions?: ApiOptionItem[];
  /** 婚姻状况（来自 init.maritalStatus） */
  maritalStatusOptions?: ApiOptionItem[];
  /** 血型 ABO（来自 init.bloodTypeABO） */
  bloodTypeAboOptions?: ApiOptionItem[];
  /** 血型 RH（来自 init.bloodTypeRH） */
  bloodTypeRhOptions?: ApiOptionItem[];
  /** 性别（来自 init.sex，与门诊挂号 AdministrativeGender code 一致） */
  sexOptions?: ApiOptionItem[];
  /** 学历（来自 init.educationLevelOptions，并与院内通用项合并） */
  educationLevelOptions?: ApiOptionItem[];
  formData?: null | Partial<Patient>;
}

const isEditMode = computed(() => !!props.formData?.id);

const activeTab = ref('basic');

const educationOptions = computed(() => props.educationLevelOptions || []);

const TAG_OPTIONS = [
  { label: '成人', value: 'ADULT' },
  { label: '儿童', value: 'CHILD' },
];

const peisForm = reactive({
  educationLevelCode: '' as string,
  pastHistory: '',
  allergySummary: '',
  familyHistory: '',
  habitSummary: '',
  tagCode: '' as string,
  remark: '',
});

function resetPeisForm() {
  peisForm.educationLevelCode = '';
  peisForm.pastHistory = '';
  peisForm.allergySummary = '';
  peisForm.familyHistory = '';
  peisForm.habitSummary = '';
  peisForm.tagCode = '';
  peisForm.remark = '';
}

async function loadPeisArchive(patientId?: string | number) {
  resetPeisForm();
  peisForm.educationLevelCode = String(props.formData?.educationLevelCode ?? '');
  if (patientId == null || patientId === '') return;
  try {
    const res = await loadArchiveApi({ patientId });
    peisForm.pastHistory = res.pastHistory || '';
    peisForm.allergySummary = res.allergySummary || '';
    peisForm.familyHistory = res.familyHistory || '';
    peisForm.habitSummary = res.habitSummary || '';
    peisForm.tagCode = res.tagCode || '';
    peisForm.remark = res.remark || '';
  } catch {
    // 无档案时保持空表
  }
}

async function savePeisArchive(patientId: string | number) {
  await saveArchiveApi({
    patientId,
    pastHistory: peisForm.pastHistory.trim() || undefined,
    allergySummary: peisForm.allergySummary.trim() || undefined,
    familyHistory: peisForm.familyHistory.trim() || undefined,
    habitSummary: peisForm.habitSummary.trim() || undefined,
    tagCode: peisForm.tagCode || undefined,
    remark: peisForm.remark.trim() || undefined,
  });
}

/** 供 schema 内异步回调使用（useVbenForm 初始化后赋值） */
const formApiHolder: { current?: ReturnType<typeof useVbenForm>[1] } = {};

/** 从中国居民身份证 15/18 位解析出生日期（YYYY-MM-DD） */
function parseChineseIdBirth(idRaw: string): null | string {
  const s = String(idRaw || '').trim();
  if (s.length === 18) {
    const y = s.slice(6, 10);
    const m = s.slice(10, 12);
    const d = s.slice(12, 14);
    return `${y}-${m}-${d}`;
  }
  if (s.length === 15) {
    const y = `19${s.slice(6, 8)}`;
    const m = s.slice(8, 10);
    const d = s.slice(10, 12);
    return `${y}-${m}-${d}`;
  }
  return null;
}

/** 后端 Boolean JSON；兼容列表缓存中的 0/1 */
function coerceLegacyBool(value: unknown, whenMissing = true): boolean {
  if (value === undefined || value === null || value === '') return whenMissing;
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number') return value !== 0;
  if (typeof value === 'string') return value === '1' || value.toLowerCase() === 'true';
  return Boolean(value);
}

function idCardFilled(values: Record<string, unknown>): boolean {
  return !!String(values?.idCard ?? '').trim();
}

/** 用证件号同步出生日期、年龄、性别（18 位规则与门诊挂号一致，见 `#/utils/format/id-card-cn`） */
function syncBirthAgeFromIdCardInput(raw: string) {
  const trimmed = String(raw ?? '').trim();
  if (!trimmed) return;
  const api = formApiHolder.current;
  if (!api) return;

  if (trimmed.length === 18) {
    const parsed = parseChineseIdCard18(trimmed);
    if (parsed) {
      void api.setFieldValue('birthDate', parsed.birthDate);
      void api.setFieldValue('age', dayjs().diff(dayjs(parsed.birthDate), 'year'));
      void api.setFieldValue('genderEnum', parsed.genderCode);
      return;
    }
  }

  const b = parseChineseIdBirth(trimmed);
  if (!b) return;
  void api.setFieldValue('birthDate', b);
  void api.setFieldValue('age', dayjs().diff(dayjs(b), 'year'));

  // 15 位大陆身份证：末位序号奇男偶女，性别 code 与 AdministrativeGender 对齐
  if (trimmed.length === 15) {
    const seqChar = trimmed.charAt(14);
    if (/\d/.test(seqChar)) {
      const genderDigit = Number.parseInt(seqChar, 10);
      if (!Number.isNaN(genderDigit)) {
        void api.setFieldValue('genderEnum', genderDigit % 2 === 1 ? 'MALE' : 'FEMALE');
      }
    }
  }
}

/** 其他证件信息列表（身份证以外的证件，对应 adm_patient_identifier），可增删行，提交时一并传给后端 */
const patientIdInfoList = ref<PatientIdInfo[]>([]);

/** 新增一行其他证件（空行，用户选择类型并填写证件号等） */
function addCertRow() {
  patientIdInfoList.value = [
    ...patientIdInfoList.value,
    {
      typeCode: '',
      identifierNo: '',
      useEnum: 1,
      startTime: '',
      endTime: '',
    },
  ];
}

/** 删除第 index 行其他证件 */
function removeCertRow(index: number) {
  const list = [...patientIdInfoList.value];
  list.splice(index, 1);
  patientIdInfoList.value = list;
}

/** 更新第 index 行某字段值 */
function updateCertRow<K extends keyof PatientIdInfo>(
  index: number,
  field: K,
  value: PatientIdInfo[K],
) {
  const list = [...patientIdInfoList.value];
  if (list[index]) {
    list[index] = { ...list[index], [field]: value };
    patientIdInfoList.value = list;
  }
}

/** 将其他证件列表转为提交参数格式（startTime/endTime 转为 yyyy-MM-dd HH:mm:ss 字符串） */
function normalizePatientIdInfoListForSubmit(
  list: PatientIdInfo[],
): CreatePatientParams['patientIdInfoList'] {
  return list
    .filter((row) => row.typeCode && row.identifierNo?.trim())
    .map((row) => ({
      useEnum: row.useEnum ?? 1,
      typeCode: String(row.typeCode),
      identifierNo: String(row.identifierNo).trim(),
      startTime: row.startTime || undefined,
      endTime: row.endTime || undefined,
    }));
}

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    activeTab.value = 'basic';
    return;
  }

  modalApi.lock(true);
  try {
    const values = await formApi.getValues();
    const regionCodes = (values.regionCodes || []) as string[];

    const baseParams = {
      ...(values as CreatePatientParams),
      addressProvince: regionCodes[0] as string,
      addressCity: regionCodes[1] as string,
      addressDistrict: regionCodes[2] as string,
      educationLevelCode: peisForm.educationLevelCode || undefined,
      patientIdInfoList: normalizePatientIdInfoListForSubmit(patientIdInfoList.value),
    };

    if (isEditMode.value) {
      if (!props.formData?.id) {
        ElMessage.error('缺少患者ID');
        return;
      }
      const params: EditPatientParams = {
        ...baseParams,
        id: props.formData.id,
      };
      await editPatientApi(params);
      await savePeisArchive(props.formData.id);
      ElMessage.success('编辑患者成功');
      emit('success');
    } else {
      const created = await createPatientApi(baseParams);
      if (created?.id != null) {
        await savePeisArchive(created.id);
      }
      ElMessage.success('创建患者成功');
      emit('success', created);
    }
    modalApi.close();
  } catch (error: unknown) {
    console.error('保存患者失败:', error);
    const err = error as { msg?: string };
    ElMessage.error(err?.msg || '保存患者失败，请稍后重试');
  } finally {
    modalApi.unlock();
  }
}

async function resetForm() {
  activeTab.value = 'basic';
  await formApi.resetForm();
  resetPeisForm();

  if (props.formData && Object.keys(props.formData).length > 0) {
    const regionCodes: string[] = [];
    if (props.formData.addressProvince) regionCodes.push(props.formData.addressProvince);
    if (props.formData.addressCity) regionCodes.push(props.formData.addressCity);
    if (props.formData.addressDistrict) regionCodes.push(props.formData.addressDistrict);

    const rawBirth = props.formData.birthDate;
    const birthDateYmd =
      rawBirth && dayjs(rawBirth).isValid() ? dayjs(rawBirth).format('YYYY-MM-DD') : rawBirth;

    await formApi.setValues({
      ...props.formData,
      birthDate: birthDateYmd,
      regionCodes,
      bloodAbo: Number(props.formData.bloodAbo) || undefined,
      bloodRh: Number(props.formData.bloodRh) || undefined,
      activeFlag: coerceLegacyBool(props.formData.activeFlag, true),
    });

    await nextTick();
    const vals = await formApi.getValues();
    const id = String(vals.idCard ?? '').trim();
    if (id) {
      const b = parseChineseIdBirth(id);
      if (b) {
        await formApi.setFieldValue('birthDate', b);
        await formApi.setFieldValue('age', dayjs().diff(dayjs(b), 'year'));
      }
    } else {
      if (vals.birthDate && (vals.age === null || vals.age === undefined || vals.age === '')) {
        await formApi.setFieldValue('age', dayjs().diff(dayjs(String(vals.birthDate)), 'year'));
      } else if (
        vals.age !== null &&
        vals.age !== undefined &&
        vals.age !== '' &&
        !Number.isNaN(Number(vals.age)) &&
        !vals.birthDate
      ) {
        await formApi.setFieldValue(
          'birthDate',
          dayjs().subtract(Number(vals.age), 'year').format('YYYY-MM-DD'),
        );
      }
    }

    // 回显其他证件列表（多条时全部展示）
    patientIdInfoList.value = (props.formData.patientIdInfoList ?? []).map((item) => ({
      id: item.id,
      useEnum: item.useEnum ?? 1,
      typeCode: item.typeCode ?? '',
      identifierNo: item.identifierNo ?? '',
      startTime: item.startTime ?? '',
      endTime: item.endTime ?? '',
    }));
    await loadPeisArchive(props.formData.id);
  } else {
    await formApi.setValues({
      name: '',
      genderEnum: undefined,
      age: undefined,
      phone: '',
      idCard: '',
      nationalityCode: '',
      workCompany: '',
      regionCodes: [],
      addressProvince: '',
      addressCity: '',
      addressDistrict: '',
      addressStreet: '',
      address: '',
      linkName: '',
      linkTelcom: '',
      linkRelationCode: undefined,
      maritalStatusEnum: undefined,
      bloodAbo: undefined,
      bloodRh: undefined,
      prfsEnum: undefined,
      activeFlag: true,
      birthDate: '',
      deceasedDate: '',
      patientIdInfoList: [],
    });
    patientIdInfoList.value = [];
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:visible', isOpen);
  },
  onOpened: async () => {
    await resetForm();
  },
  onConfirm: handleSubmit,
  showCancelButton: true,
  showConfirmButton: true,
  confirmText: '确定',
  cancelText: '取消',
});

const formConfig = reactive({
  commonConfig: {
    componentProps: {
      style: { width: '220px' },
    },
  },
  layout: 'horizontal' as const,
  wrapperClass: 'grid-cols-1 sm:grid-cols-2',
  schema: computed(() => [
    {
      component: 'Input',
      fieldName: 'name',
      label: '姓名',
      required: true,
      rules: z.preprocess(
        (v) => (v === null || v === undefined ? '' : String(v)).trim(),
        z.string().min(1, { message: '请输入姓名' }),
      ),
      componentProps: {
        placeholder: '请输入姓名',
      },
    },
    {
      component: 'RadioGroup',
      fieldName: 'genderEnum',
      label: '性别',
      required: true,
      rules: z.preprocess(
        (v) => (v === null || v === undefined ? '' : String(v)),
        z.string().min(1, { message: '请选择性别' }),
      ),
      componentProps: {
        options: (props.sexOptions ?? []).map((opt) => ({
          label: opt.label,
          value: opt.value,
        })),
      },
    },
    {
      component: 'Input',
      fieldName: 'idCard',
      label: '证件号',
      required: true,
      componentProps: {
        placeholder: '请输入证件号（填后自动解析性别、出生日期与年龄）',
        onBlur: (e: FocusEvent) => {
          const el = e.target as HTMLInputElement | null;
          syncBirthAgeFromIdCardInput(el?.value ?? '');
        },
        onInput: (e: Event) => {
          const el = e.target as HTMLInputElement | null;
          const raw = String(el?.value ?? '').trim();
          if (raw.length === 15 || raw.length === 18) {
            syncBirthAgeFromIdCardInput(raw);
          }
        },
      },
      rules: z.preprocess(
        (v) => (v === null || v === undefined ? '' : String(v)).trim(),
        z
          .string()
          .min(1, { message: '请输入证件号' })
          .refine((t) => isAcceptableChineseIdCardInput(t), {
            message: '请输入正确的身份证号格式（15位或18位）',
          }),
      ),
    },
    {
      component: 'Input',
      fieldName: 'phone',
      label: '电话',
      componentProps: {
        placeholder: '请输入电话',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'age',
      label: '年龄',
      required: true,
      rules: z.preprocess(
        (v) => (v === null || v === undefined || v === '' ? undefined : Number(v)),
        z
          .number({ invalid_type_error: '请输入年龄', required_error: '请输入年龄' })
          .min(0, { message: '年龄不能小于0' })
          .max(150, { message: '年龄不能大于150' }),
      ),
      dependencies: {
        componentProps(values: Record<string, unknown>) {
          const blocked = idCardFilled(values);
          return {
            min: 0,
            max: 150,
            controlsPosition: 'right' as const,
            placeholder: blocked ? '已填证件号，由证件推算' : '输入年龄自动推算出生日期',
            disabled: blocked,
            onChange: (val: null | number | undefined) => {
              void (async () => {
                const api = formApiHolder.current;
                if (!api) return;
                const v = await api.getValues();
                if (idCardFilled(v as Record<string, unknown>)) return;
                if (val !== null && val !== undefined && !Number.isNaN(Number(val))) {
                  await api.setFieldValue(
                    'birthDate',
                    dayjs().subtract(Number(val), 'year').format('YYYY-MM-DD'),
                  );
                }
              })();
            },
          };
        },
        triggerFields: ['idCard'],
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'birthDate',
      label: '出生日期',
      required: true,
      rules: z.preprocess(
        (v) => (v === null || v === undefined ? '' : String(v)).trim(),
        z.string().min(1, { message: '请选择出生日期' }),
      ),
      componentProps: {
        type: 'date',
        style: { width: '220px' },
        format: 'YYYY-MM-DD',
        valueFormat: 'YYYY-MM-DD',
      },
      dependencies: {
        componentProps(values: Record<string, unknown>) {
          const hasId = idCardFilled(values);
          const ag = values?.age;
          const hasAge = ag !== null && ag !== undefined && ag !== '' && !Number.isNaN(Number(ag));
          // 仅「有年龄、尚未填证件」时禁用出生日；一旦填了证件号须解除禁用，否则 DatePicker 在 disabled 下往往不刷新显示
          return {
            disabled: hasAge && !hasId,
            placeholder: hasId ? '由证件号解析' : hasAge ? '由年龄自动推算' : '请选择出生日期',
          };
        },
        triggerFields: ['idCard', 'age'],
      },
    },
    {
      component: 'Select',
      fieldName: 'nationalityCode',
      label: '国籍',
      componentProps: {
        placeholder: '请选择国籍',
        filterable: true,
        options: [...countryList]
          .toSorted((a, b) => {
            if (a.cnname === '中国') return -1;
            if (b.cnname === '中国') return 1;
            return a.cnname.localeCompare(b.cnname || '');
          })
          .map((item) => ({
            label: item.cnname,
            value: item.alpha3,
          })),
      },
    },
    {
      component: 'Input',
      fieldName: 'workCompany',
      label: '工作单位',
      componentProps: {
        placeholder: '请输入工作单位',
      },
    },
    {
      component: 'Select',
      fieldName: 'maritalStatusEnum',
      label: '婚姻状况',
      componentProps: {
        placeholder: '请选择婚姻状况',
        clearable: true,
        options: (props.maritalStatusOptions ?? []).map((opt) => ({
          label: opt.label,
          value: opt.value === null || opt.value === undefined ? undefined : Number(opt.value),
        })),
      },
    },
    {
      component: 'Select',
      fieldName: 'bloodAbo',
      label: '血型',
      componentProps: {
        placeholder: '请选择血型',
        clearable: true,
        options: (props.bloodTypeAboOptions ?? []).map((opt) => ({
          label: opt.label,
          value: Number.isNaN(Number(opt.value)) ? undefined : Number(opt.value),
        })),
      },
    },
    {
      component: 'Select',
      fieldName: 'bloodRh',
      label: 'Rh 血型',
      componentProps: {
        placeholder: '请选择 Rh 血型',
        clearable: true,
        options: (props.bloodTypeRhOptions ?? []).map((opt) => ({
          label: opt.label,
          value: Number.isNaN(Number(opt.value)) ? undefined : Number(opt.value),
        })),
      },
    },
    // {
    //   component: 'InputNumber',
    //   fieldName: 'prfsEnum',
    //   label: '职业代码',
    //   componentProps: {
    //     placeholder: '请输入职业代码',
    //     min: 0,
    //   },
    // },
    {
      component: 'DatePicker',
      fieldName: 'deceasedDate',
      label: '死亡日期',
      componentProps: {
        type: 'date',
        placeholder: '请选择死亡日期',
        style: { width: '220px' },
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'YYYY-MM-DD HH:mm:ss',
      },
    },
    // {
    //   component: 'RadioGroup',
    //   fieldName: 'activeFlag',
    //   label: '状态',
    //   componentProps: {
    //     options: [
    //       { label: '启用', value: 1 },
    //       { label: '停用', value: 0 },
    //       ],
    //     },
    //   },
    {
      component: SelectArea,
      fieldName: 'regionCodes',
      label: '省市区',
      modelPropName: 'modelValue',
      componentProps: {
        maxLevel: 3,
        placeholder: '请选择省市区',
      },
    },
    {
      component: 'Input',
      fieldName: 'address',
      label: '详细地址',
      componentProps: {
        placeholder: '请输入详细地址',
      },
    },
    {
      component: 'Input',
      fieldName: 'linkName',
      label: '联系人姓名',
      componentProps: {
        placeholder: '请输入联系人姓名',
      },
    },
    {
      component: 'Input',
      fieldName: 'linkTelcom',
      label: '联系人电话',
      componentProps: {
        placeholder: '请输入联系人电话',
      },
    },
  ]),
  showDefaultActions: false,
});

const [Form, formApi] = useVbenForm(formConfig);
formApiHolder.current = formApi;

type CertColumn = {
  columnProps: Record<string, boolean | number | string>;
  component?: typeof ElDatePicker | typeof ElInput | typeof ElSelect;
  getProps?: (row: PatientIdInfo, idx: number) => Record<string, unknown>;
  key: string;
  type?: 'action';
};

const certTableColumns = computed<CertColumn[]>(() => [
  {
    key: 'typeCode',
    columnProps: { label: '证件类型', minWidth: 160 },
    component: ElSelect,
    getProps: (row, idx) => ({
      modelValue: row.typeCode,
      'onUpdate:modelValue': (v: null | PatientIdInfo['typeCode'] | undefined) =>
        updateCertRow(idx, 'typeCode', (v ?? '') as PatientIdInfo['typeCode']),
      placeholder: '请选择',
      clearable: true,
      filterable: true,
      size: 'small',
      style: 'width: 100%',
    }),
  },
  {
    key: 'identifierNo',
    columnProps: { label: '证件号', minWidth: 160 },
    component: ElInput,
    getProps: (row, idx) => ({
      modelValue: row.identifierNo,
      'onUpdate:modelValue': (v: null | string | undefined) =>
        updateCertRow(idx, 'identifierNo', String(v ?? '')),
      placeholder: '请输入证件号',
      size: 'small',
      clearable: true,
    }),
  },
  {
    key: 'useEnum',
    columnProps: { label: '用途', width: 100 },
    component: ElSelect,
    getProps: (row, idx) => ({
      modelValue: row.useEnum,
      'onUpdate:modelValue': (v: null | number | undefined) =>
        updateCertRow(idx, 'useEnum', (v ?? 1) as PatientIdInfo['useEnum']),
      placeholder: '请选择',
      size: 'small',
      style: 'width: 100%',
    }),
  },
  {
    key: 'startTime',
    columnProps: { label: '有效期开始', width: 160 },
    component: ElDatePicker,
    getProps: (row, idx) => ({
      modelValue: row.startTime || undefined,
      'onUpdate:modelValue': (v: null | string | undefined) =>
        updateCertRow(idx, 'startTime', v ?? ''),
      type: 'datetime',
      valueFormat: 'YYYY-MM-DD HH:mm:ss',
      format: 'YYYY-MM-DD HH:mm',
      placeholder: '选择日期',
      size: 'small',
      style: 'width: 100%',
      teleported: true,
    }),
  },
  {
    key: 'endTime',
    columnProps: { label: '有效期结束', width: 160 },
    component: ElDatePicker,
    getProps: (row, idx) => ({
      modelValue: row.endTime || undefined,
      'onUpdate:modelValue': (v: null | string | undefined) =>
        updateCertRow(idx, 'endTime', v ?? ''),
      type: 'datetime',
      valueFormat: 'YYYY-MM-DD HH:mm:ss',
      format: 'YYYY-MM-DD HH:mm',
      placeholder: '选择日期',
      size: 'small',
      style: 'width: 100%',
      teleported: true,
    }),
  },
  {
    key: 'action',
    type: 'action',
    columnProps: { label: '操作', width: 70, fixed: 'right' },
  },
]);

watch(
  () => props.visible,
  async (visible) => {
    if (visible) {
      modalApi.open();
    } else {
      modalApi.close();
      await formApi.resetForm();
    }
  },
  { immediate: true },
);
</script>

<template>
  <Modal
    class="w-[840px]"
    :title="isEditMode ? '编辑患者' : '新增患者'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <ElTabs v-model="activeTab">
      <ElTabPane label="基本信息" name="basic">
        <Form />
        <!-- 其他证件信息（身份证以外，可多条，存 adm_patient_identifier） -->
        <div class="mt-4">
          <div class="mb-2 flex items-center justify-between">
            <span class="text-sm font-medium text-gray-700">其他证件信息</span>
            <ElButton type="primary" size="small" @click="addCertRow">新增一行</ElButton>
          </div>
          <ElTable :data="patientIdInfoList" border size="small" max-height="280">
            <ElTableColumn v-for="col in certTableColumns" :key="col.key" v-bind="col.columnProps">
              <template #default="{ row, $index: idx }">
                <template v-if="col.type === 'action'">
                  <ElButton type="danger" link size="small" @click="removeCertRow(idx)">
                    删除
                  </ElButton>
                </template>
                <template v-else>
                  <component :is="col.component" v-bind="col.getProps?.(row, idx)">
                    <template v-if="col.key === 'typeCode'">
                      <ElOption
                        v-for="opt in certTypeOptions"
                        :key="String(opt.value)"
                        :label="opt.label"
                        :value="opt.value ?? ''"
                      />
                    </template>
                    <template v-else-if="col.key === 'useEnum'">
                      <ElOption
                        v-for="opt in USE_ENUM_OPTIONS"
                        :key="opt.value"
                        :label="opt.label"
                        :value="opt.value"
                      />
                    </template>
                  </component>
                </template>
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
      </ElTabPane>

      <ElTabPane label="体检" name="peis">
        <ElForm label-width="88px" class="pr-2">
          <ElFormItem label="学历">
            <ElSelect
              v-model="peisForm.educationLevelCode"
              clearable
              filterable
              placeholder="请选择"
              style="width: 220px"
            >
              <ElOption
                v-for="opt in educationOptions"
                :key="String(opt.value)"
                :label="opt.label"
                :value="String(opt.value)"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="标签">
            <ElSelect
              v-model="peisForm.tagCode"
              clearable
              placeholder="请选择"
              style="width: 220px"
            >
              <ElOption
                v-for="opt in TAG_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="既往史">
            <ElInput
              v-model="peisForm.pastHistory"
              type="textarea"
              :rows="2"
              placeholder="如：高血压；无则填「无」"
            />
          </ElFormItem>
          <ElFormItem label="过敏史">
            <ElInput
              v-model="peisForm.allergySummary"
              type="textarea"
              :rows="2"
              placeholder="如：青霉素过敏；无则填「无」"
            />
          </ElFormItem>
          <ElFormItem label="家族史">
            <ElInput
              v-model="peisForm.familyHistory"
              type="textarea"
              :rows="2"
              placeholder="选填"
            />
          </ElFormItem>
          <ElFormItem label="生活习惯">
            <ElInput v-model="peisForm.habitSummary" type="textarea" :rows="2" placeholder="选填" />
          </ElFormItem>
          <ElFormItem label="备注">
            <ElInput v-model="peisForm.remark" type="textarea" :rows="2" placeholder="选填" />
          </ElFormItem>
        </ElForm>
      </ElTabPane>
    </ElTabs>
  </Modal>
</template>
