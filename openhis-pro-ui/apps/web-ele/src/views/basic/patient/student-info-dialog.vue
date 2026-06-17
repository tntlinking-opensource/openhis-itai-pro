<script lang="ts" setup>
import type { Patient, PatientStudentDto, SavePatientStudentParams } from '#/api/basic/patient';
import type { ApiOptionItem } from '#/types/api-response';

import { reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  ElButton,
  ElDatePicker,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
} from 'element-plus';

import {
  deletePatientStudentApi,
  getPatientStudentApi,
  savePatientStudentApi,
} from '#/api/basic/patient';

const props = withDefaults(
  defineProps<{
    educationLevelOptions?: ApiOptionItem[];
    /** 当前行患者（需已校验证件号非空） */
    patient: null | Patient;
    physicalExamResultOptions?: ApiOptionItem[];
    /** 性别下拉展示用（与患者 init.sex 一致） */
    sexOptions?: ApiOptionItem[];
    studentStatusOptions?: ApiOptionItem[];
    studyModeOptions?: ApiOptionItem[];
    visible?: boolean;
  }>(),
  {
    visible: false,
    patient: null,
    sexOptions: () => [],
    educationLevelOptions: () => [],
    studyModeOptions: () => [],
    studentStatusOptions: () => [],
    physicalExamResultOptions: () => [],
  },
);

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

/** 表单模型（姓名/性别/证件号只读，来自患者；保存时后端再次从患者主表同步） */
const form = reactive({
  id: '' as string | undefined,
  patientId: '',
  name: '',
  gender: 0,
  idNumber: '',
  age: undefined as number | undefined,
  studentId: '',
  phone: '',
  college: '',
  major: '',
  educationLevel: undefined as number | undefined,
  enrollmentDate: '',
  graduationDate: '',
  grade: '',
  studyMode: undefined as number | undefined,
  studentStatus: undefined as number | undefined,
  physicalExamResult: undefined as number | undefined,
  counselor: '',
  counselorPhone: '',
});

const loading = ref(false);

function resetEmpty(p: Patient) {
  form.id = undefined;
  form.patientId = String(p.id);
  form.name = p.name || '';
  form.gender = p.genderEnum ?? 0;
  form.idNumber = (p.idCard || '').trim();
  form.age = undefined;
  form.studentId = '';
  form.phone = p.phone || '';
  form.college = '';
  form.major = '';
  form.educationLevel = undefined;
  form.enrollmentDate = '';
  form.graduationDate = '';
  form.grade = '';
  form.studyMode = undefined;
  form.studentStatus = undefined;
  form.physicalExamResult = undefined;
  form.counselor = '';
  form.counselorPhone = '';
}

function applyDto(d: PatientStudentDto) {
  form.id = d.id ? String(d.id) : undefined;
  form.patientId = String(d.patientId);
  form.name = d.name || '';
  form.gender = d.gender ?? 0;
  form.idNumber = d.idNumber || '';
  form.age = d.age ?? undefined;
  form.studentId = d.studentId || '';
  form.phone = d.phone || '';
  form.college = d.college || '';
  form.major = d.major || '';
  form.educationLevel = d.educationLevel ?? undefined;
  form.enrollmentDate = d.enrollmentDate || '';
  form.graduationDate = d.graduationDate || '';
  form.grade = d.grade || '';
  form.studyMode = d.studyMode ?? undefined;
  form.studentStatus = d.studentStatus ?? undefined;
  form.physicalExamResult = d.physicalExamResult ?? undefined;
  form.counselor = d.counselor || '';
  form.counselorPhone = d.counselorPhone || '';
}

async function loadStudent() {
  const p = props.patient;
  if (!p?.id) return;
  loading.value = true;
  try {
    const dto = await getPatientStudentApi(String(p.id));
    applyDto(dto);
  } catch (error: unknown) {
    console.error(error);
    const err = error as { message?: string; msg?: string };
    ElMessage.error(err?.msg || err?.message || '加载学籍失败');
    resetEmpty(p);
  } finally {
    loading.value = false;
  }
}

async function handleSave() {
  if (!form.patientId) return;
  const studentIdTrim = (form.studentId || '').trim();
  const gradeTrim = (form.grade || '').trim();
  if (!studentIdTrim) {
    ElMessage.warning('请输入学号');
    return;
  }
  if (!gradeTrim) {
    ElMessage.warning('请输入年级');
    return;
  }
  const params: SavePatientStudentParams = {
    patientId: form.patientId,
    age: form.age ?? null,
    studentId: studentIdTrim,
    phone: form.phone || undefined,
    college: form.college || undefined,
    major: form.major || undefined,
    educationLevel: form.educationLevel ?? null,
    enrollmentDate: form.enrollmentDate || undefined,
    graduationDate: form.graduationDate || undefined,
    grade: gradeTrim,
    studyMode: form.studyMode ?? null,
    studentStatus: form.studentStatus ?? null,
    physicalExamResult: form.physicalExamResult ?? null,
    counselor: form.counselor || undefined,
    counselorPhone: form.counselorPhone || undefined,
  };
  modalApi.lock(true);
  try {
    await savePatientStudentApi(params);
    ElMessage.success('学籍保存成功');
    emit('success');
    modalApi.close();
  } catch (error: unknown) {
    const err = error as { message?: string; msg?: string };
    ElMessage.error(err?.msg || err?.message || '保存失败');
  } finally {
    modalApi.unlock();
  }
}

async function handleDelete() {
  if (!form.patientId || !form.id) {
    ElMessage.warning('当前无学籍记录可删除');
    return;
  }
  try {
    await ElMessageBox.confirm('确定删除该患者的学籍信息吗？', '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    });
    modalApi.lock(true);
    await deletePatientStudentApi(form.patientId);
    ElMessage.success('已删除学籍');
    emit('success');
    modalApi.close();
  } catch (error: unknown) {
    if (error !== 'cancel') {
      const err = error as { message?: string; msg?: string };
      ElMessage.error(err?.msg || err?.message || '删除失败');
    }
  } finally {
    modalApi.unlock();
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (open: boolean) => {
    emit('update:visible', open);
  },
  onOpened: () => {
    if (props.patient) {
      void loadStudent();
    }
  },
  showCancelButton: true,
  showConfirmButton: false,
  cancelText: '关闭',
});

watch(
  () => props.visible,
  (v) => {
    if (v) modalApi.open();
    else modalApi.close();
  },
  { immediate: true },
);
</script>

<template>
  <Modal class="w-[720px]" title="学籍信息" :close-on-click-modal="false">
    <div v-loading="loading" class="max-h-[70vh] overflow-y-auto pr-1">
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div>
          <div class="mb-1 text-sm text-gray-600">姓名</div>
          <ElInput v-model="form.name" disabled placeholder="姓名" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">性别</div>
          <ElSelect v-model="form.gender" disabled placeholder="性别" class="w-full">
            <ElOption
              v-for="opt in sexOptions"
              :key="String(opt.value)"
              :label="opt.label"
              :value="Number(opt.value)"
            />
          </ElSelect>
        </div>
        <div class="sm:col-span-2">
          <div class="mb-1 text-sm text-gray-600">身份证号</div>
          <ElInput v-model="form.idNumber" disabled placeholder="身份证号" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">年龄</div>
          <ElInputNumber
            v-model="form.age"
            :min="0"
            :max="150"
            placeholder="年龄"
            class="w-full"
            controls-position="right"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600"><span class="text-red-500">*</span> 学号</div>
          <ElInput v-model="form.studentId" clearable placeholder="请输入学号（必填）" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">电话</div>
          <ElInput v-model="form.phone" clearable placeholder="电话" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">学院</div>
          <ElInput v-model="form.college" clearable placeholder="学院" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">专业</div>
          <ElInput v-model="form.major" clearable placeholder="专业" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">学历层次</div>
          <ElSelect v-model="form.educationLevel" clearable placeholder="请选择" class="w-full">
            <ElOption
              v-for="opt in educationLevelOptions"
              :key="String(opt.value)"
              :label="opt.label"
              :value="Number(opt.value)"
            />
          </ElSelect>
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600"><span class="text-red-500">*</span> 年级</div>
          <ElInput v-model="form.grade" clearable placeholder="请输入年级（必填）" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">入校时间</div>
          <ElDatePicker
            v-model="form.enrollmentDate"
            type="datetime"
            class="w-full"
            value-format="YYYY-MM-DD HH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="入校时间"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">离校时间</div>
          <ElDatePicker
            v-model="form.graduationDate"
            type="datetime"
            class="w-full"
            value-format="YYYY-MM-DD HH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="离校时间"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">学习形式</div>
          <ElSelect v-model="form.studyMode" clearable placeholder="请选择" class="w-full">
            <ElOption
              v-for="opt in studyModeOptions"
              :key="String(opt.value)"
              :label="opt.label"
              :value="Number(opt.value)"
            />
          </ElSelect>
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">在校状态</div>
          <ElSelect v-model="form.studentStatus" clearable placeholder="请选择" class="w-full">
            <ElOption
              v-for="opt in studentStatusOptions"
              :key="String(opt.value)"
              :label="opt.label"
              :value="Number(opt.value)"
            />
          </ElSelect>
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">体检结果</div>
          <ElSelect v-model="form.physicalExamResult" clearable placeholder="请选择" class="w-full">
            <ElOption
              v-for="opt in physicalExamResultOptions"
              :key="String(opt.value)"
              :label="opt.label"
              :value="Number(opt.value)"
            />
          </ElSelect>
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">辅导员</div>
          <ElInput v-model="form.counselor" clearable placeholder="辅导员" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">辅导员电话</div>
          <ElInput v-model="form.counselorPhone" clearable placeholder="辅导员电话" />
        </div>
      </div>
    </div>
    <template #prepend-footer>
      <div class="flex w-full flex-wrap items-center justify-end gap-2">
        <ElButton v-if="form.id" type="danger" @click="handleDelete">删除学籍</ElButton>
        <ElButton type="primary" @click="handleSave">保存</ElButton>
      </div>
    </template>
  </Modal>
</template>
