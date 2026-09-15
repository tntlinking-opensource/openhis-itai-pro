<script setup lang="ts">
/**
 * 病案首页「诊断记录」整章：吸收老 EMR 布局——
 * 顶栏门急诊诊断、入院|出院双表、底栏损伤/病理与统计项。
 */
import type { OptionDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station.types';
import type { DiagnosisLineDto } from '#/api/mrms/types';
import type { DiagnosisConfirmPayload } from '#/components/doctor-station/diagnosis/services/diagnosis.types';
import type { HomepageFormModel } from '../homepage-form.types';

import { computed, ref, watch } from 'vue';

import { ElFormItem, ElInput, ElInputNumber, ElMessage, ElOption, ElSelect } from 'element-plus';

import { httpLoadAdmissionParticipantInit } from '#/api/inpatient/nurse-station/inpatient-nurse-station.http';
import { DiagnosisPickerDialog } from '#/components/doctor-station';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  CLINIC_PATH_MANAGE_OPTIONS,
  DIAG_MATCH_OPTIONS,
  MAIN_DIAG_CURE_OPTIONS,
} from '../../mrms.constants';
import { HOMEPAGE_EMPTY_DISPLAY } from '../homepage.constants';
import { displayHomepageEmpty, newDiagnosisRow } from '../homepage-form.util';
import HomepageDiagnosisGrid from './homepage-diagnosis-grid.vue';

defineOptions({ name: 'HomepageDiagnosisSection' });

interface Props {
  readonly?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  readonly: false,
});

const form = defineModel<HomepageFormModel>('modelValue', { required: true });

const emit = defineEmits<{
  dirty: [];
}>();

/** 顶栏/底栏选码目标 */
type SummaryPickKind = 'injury_poison' | 'otp_tcm' | 'otp_wm' | 'pathology';

/** 当前选码目标 */
const pickKind = ref<null | SummaryPickKind>(null);
const pickerVisible = ref(false);

/** 住院科室出诊医生（出院诊断下拉） */
const doctorOptions = ref<OptionDto[]>([]);

const diagnoses = computed({
  get: () => form.value.diagnoses ?? [],
  set: (v: DiagnosisLineDto[]) => {
    form.value.diagnoses = v;
  },
});

/** 标脏 */
function markDirty() {
  emit('dirty');
}

/**
 * 将接口 Option 规范为 string value（防 Long 精度丢失）。
 *
 * @param raw 接口下拉项
 */
function mapDoctorOptions(raw: OptionDto[] | undefined): OptionDto[] {
  return (raw ?? [])
    .map((item) => ({
      label: item.label,
      value: String(item.value),
    }))
    .filter((item) => item.label && item.value);
}

/**
 * 按入院科室拉医生下拉（同住院登记主治医生口径）。
 * 科室岗位无命中时回退全院医师，避免「无科室医生」空白。
 *
 * @param organizationId 入院科室主键
 */
async function loadDoctorOptions(organizationId?: string) {
  const orgId = preserveApiIdForRequest(organizationId);
  try {
    const scoped = await httpLoadAdmissionParticipantInit(orgId ? { organizationId: orgId } : {});
    let options = mapDoctorOptions(scoped.doctorOptions);
    // 入院科室无激活医师岗时回退全院，保证下拉可用
    if (options.length === 0 && orgId) {
      const fallback = await httpLoadAdmissionParticipantInit({});
      options = mapDoctorOptions(fallback.doctorOptions);
    }
    doctorOptions.value = options;
  } catch {
    doctorOptions.value = [];
  }
}

/**
 * 取特殊类型首行（病理/损伤各一行展示）。
 *
 * @param typeCode 诊断类型
 */
function findSpecialRow(typeCode: 'injury_poison' | 'pathology'): DiagnosisLineDto | undefined {
  return (form.value.diagnoses ?? []).find((r) => r.diagTypeCode === typeCode);
}

/**
 * 打开诊断选码弹窗（门急诊标量 / 损伤病理子表行）。
 *
 * @param kind 选码目标
 */
function openPicker(kind: SummaryPickKind) {
  if (props.readonly) {
    return;
  }
  pickKind.value = kind;
  pickerVisible.value = true;
}

/**
 * 将选中诊断写入门急诊正式列。
 *
 * @param kind otp_wm | otp_tcm
 * @param code 疾病编码
 * @param name 诊断名称
 */
function applyOtpPick(kind: 'otp_tcm' | 'otp_wm', code: string, name: string) {
  if (kind === 'otp_wm') {
    form.value.otpWmDise = name;
    form.value.wmDiseCode = code;
    return;
  }
  form.value.otpTcmDise = name;
  form.value.tcmDiseCode = code;
}

/**
 * 将选中诊断写入损伤/病理子表行（各类型最多一行）。
 *
 * @param kind injury_poison | pathology
 * @param code 疾病编码
 * @param name 诊断名称
 */
function applySpecialRowPick(kind: 'injury_poison' | 'pathology', code: string, name: string) {
  const list = [...(form.value.diagnoses ?? [])];
  const idx = list.findIndex((r) => r.diagTypeCode === kind);
  if (idx >= 0) {
    const row = list[idx];
    if (row) {
      row.diagCode = code;
      row.diagName = name;
    }
  } else {
    const row = newDiagnosisRow(kind, list.length + 1);
    row.diagCode = code;
    row.diagName = name;
    list.push(row);
  }
  // 保持入院→出院→特殊顺序
  const admission = list.filter(
    (r) => r.diagTypeCode === 'admission_main' || r.diagTypeCode === 'admission_other',
  );
  const discharge = list.filter(
    (r) => r.diagTypeCode === 'discharge_main' || r.diagTypeCode === 'discharge_other',
  );
  const special = list.filter(
    (r) => r.diagTypeCode === 'pathology' || r.diagTypeCode === 'injury_poison',
  );
  const next = [...admission, ...discharge, ...special];
  next.forEach((row, i) => {
    row.sortNo = i + 1;
  });
  form.value.diagnoses = next;
}

/** 诊断选码弹窗确认 */
function handlePickerConfirm(payload: DiagnosisConfirmPayload) {
  const kind = pickKind.value;
  pickKind.value = null;
  if (!kind) {
    return;
  }
  const code = (payload.selected?.code ?? '').trim();
  const name = (payload.selected?.name ?? '').trim();
  if (!code && !name) {
    ElMessage.warning('未选中有效诊断');
    return;
  }
  if (kind === 'otp_wm' || kind === 'otp_tcm') {
    applyOtpPick(kind, code, name);
  } else {
    applySpecialRowPick(kind, code, name);
  }
  markDirty();
  ElMessage.success('已更新，保存病案首页后生效');
}

// 入院科室变化时刷新医生下拉
watch(
  () => form.value.organizationId,
  (orgId) => {
    void loadDoctorOptions(orgId);
  },
  { immediate: true },
);
</script>

<template>
  <div class="hp-diag-section">
    <!-- 顶栏：门急诊西医/中医（对齐老页：只读名称 + 点选诊断目录） -->
    <div class="hp-diag-section__summary">
      <ElFormItem
        label="门（急）诊诊断（西医）"
        class="hp-diag-section__item hp-diag-section__item--pick"
      >
        <ElInput
          :model-value="form.otpWmDise"
          :disabled="readonly"
          readonly
          :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          class="cursor-pointer"
          @click="openPicker('otp_wm')"
        />
      </ElFormItem>
      <ElFormItem label="国临码" class="hp-diag-section__item hp-diag-section__item--code">
        <span class="hp-diag-section__code-text">
          {{ displayHomepageEmpty(form.wmDiseCode) }}
        </span>
      </ElFormItem>
      <ElFormItem
        label="门（急）诊诊断（中医）"
        class="hp-diag-section__item hp-diag-section__item--pick"
      >
        <ElInput
          :model-value="form.otpTcmDise"
          :disabled="readonly"
          readonly
          :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          class="cursor-pointer"
          @click="openPicker('otp_tcm')"
        />
      </ElFormItem>
      <ElFormItem label="中医疾病编码" class="hp-diag-section__item hp-diag-section__item--code">
        <span class="hp-diag-section__code-text">
          {{ displayHomepageEmpty(form.tcmDiseCode) }}
        </span>
      </ElFormItem>
    </div>

    <!-- 入院较窄、出院较宽（出院列多） -->
    <div class="hp-diag-section__dual">
      <div class="hp-diag-section__col hp-diag-section__col--admission">
        <HomepageDiagnosisGrid
          v-model="diagnoses"
          group="admission"
          title="入院诊断"
          :readonly="readonly"
          @dirty="markDirty"
        />
      </div>
      <div class="hp-diag-section__col hp-diag-section__col--discharge">
        <HomepageDiagnosisGrid
          v-model="diagnoses"
          group="discharge"
          title="出院诊断"
          :readonly="readonly"
          :doctor-options="doctorOptions"
          @dirty="markDirty"
        />
      </div>
    </div>

    <!-- 底栏：损伤中毒 / 病理 + 统计 -->
    <div class="hp-diag-section__footer">
      <div class="hp-diag-section__footer-row">
        <ElFormItem label="损伤、中毒的外部原因" class="hp-diag-section__item">
          <ElInput
            :model-value="findSpecialRow('injury_poison')?.diagName ?? ''"
            :disabled="readonly"
            readonly
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            class="cursor-pointer"
            @click="openPicker('injury_poison')"
          />
        </ElFormItem>
        <ElFormItem label="国临码" class="hp-diag-section__item hp-diag-section__item--code">
          <span class="hp-diag-section__code-text">
            {{ displayHomepageEmpty(findSpecialRow('injury_poison')?.diagCode) }}
          </span>
        </ElFormItem>
        <ElFormItem
          label="主要诊断治愈好转"
          class="hp-diag-section__item hp-diag-section__item--sm"
        >
          <ElSelect
            v-model="form.mainDiagCureCode"
            clearable
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in MAIN_DIAG_CURE_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="抢救" class="hp-diag-section__item hp-diag-section__item--rescue">
          <div class="hp-diag-section__rescue">
            <ElInputNumber
              v-model="form.rescueCount"
              :min="0"
              :disabled="readonly"
              controls-position="right"
              @change="markDirty"
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            />
            <span>次，成功</span>
            <ElInputNumber
              v-model="form.rescueSuccessCount"
              :min="0"
              :disabled="readonly"
              controls-position="right"
              @change="markDirty"
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            />
            <span>次</span>
          </div>
        </ElFormItem>
      </div>

      <div class="hp-diag-section__footer-row">
        <ElFormItem label="病理诊断" class="hp-diag-section__item">
          <ElInput
            :model-value="findSpecialRow('pathology')?.diagName ?? ''"
            :disabled="readonly"
            readonly
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            class="cursor-pointer"
            @click="openPicker('pathology')"
          />
        </ElFormItem>
        <ElFormItem label="国临码" class="hp-diag-section__item hp-diag-section__item--code">
          <span class="hp-diag-section__code-text">
            {{ displayHomepageEmpty(findSpecialRow('pathology')?.diagCode) }}
          </span>
        </ElFormItem>
        <ElFormItem label="病理号" class="hp-diag-section__item hp-diag-section__item--sm">
          <ElInput
            v-model="form.pathologyNo"
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
        <ElFormItem label="临床路径管理" class="hp-diag-section__item hp-diag-section__item--sm">
          <ElSelect
            v-model="form.clinicPathManageCode"
            clearable
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in CLINIC_PATH_MANAGE_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
      </div>

      <div class="hp-diag-section__footer-row hp-diag-section__match">
        <span class="hp-diag-section__match-label">诊断符合情况</span>
        <ElFormItem label="门诊与出院" class="hp-diag-section__match-item">
          <ElSelect
            v-model="form.diagMatchOpDscg"
            clearable
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in DIAG_MATCH_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="入院与出院" class="hp-diag-section__match-item">
          <ElSelect
            v-model="form.diagMatchAdmDscg"
            clearable
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in DIAG_MATCH_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="术前与术后" class="hp-diag-section__match-item">
          <ElSelect
            v-model="form.diagMatchPrePost"
            clearable
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in DIAG_MATCH_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="临床与病理" class="hp-diag-section__match-item">
          <ElSelect
            v-model="form.diagMatchClinicPath"
            clearable
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in DIAG_MATCH_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="放射与病理" class="hp-diag-section__match-item">
          <ElSelect
            v-model="form.diagMatchRadioPath"
            clearable
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in DIAG_MATCH_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
      </div>
    </div>

    <DiagnosisPickerDialog
      v-model="pickerVisible"
      :pick-only="true"
      station-scope="inpatient"
      @confirm="handlePickerConfirm"
    />
  </div>
</template>

<style scoped>
.hp-diag-section__summary,
.hp-diag-section__footer-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-bottom: 12px;
  align-items: flex-start;
}

.hp-diag-section__item {
  margin-bottom: 0;
  flex: 1 1 280px;
  min-width: 240px;
}

.hp-diag-section__item--pick {
  flex: 1 1 320px;
  min-width: 280px;
}

.hp-diag-section__item :deep(.el-form-item__label) {
  white-space: nowrap;
  width: auto !important;
  min-width: 9.5em;
}

.hp-diag-section__item--code {
  flex: 0 1 220px;
  min-width: 180px;
}

.hp-diag-section__item--sm {
  flex: 0 1 180px;
}

.hp-diag-section__item--rescue {
  flex: 1 1 300px;
}

.hp-diag-section__code-text {
  color: var(--el-text-color-primary);
  line-height: 32px;
}

.hp-diag-section__dual {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 16px;
}

.hp-diag-section__col {
  min-width: 0;
  width: 100%;
}

.hp-diag-section__col--discharge {
  overflow: visible;
}

.hp-diag-section__rescue {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-regular);
}

.hp-diag-section__match {
  align-items: center;
}

.hp-diag-section__match-label {
  color: var(--el-text-color-primary);
  font-weight: 600;
  margin-right: 8px;
  white-space: nowrap;
}

.hp-diag-section__match-item {
  margin-bottom: 0;
  flex: 0 1 160px;
}
</style>
