<script setup lang="ts">
import type { HomepageFormModel } from '../homepage-form.types';

import { watch } from 'vue';

import { ElDatePicker, ElFormItem, ElInput, ElInputNumber, ElOption, ElSelect } from 'element-plus';

import SelectArea from '#/components/select-area.vue';

import {
  ABO_OPTIONS,
  ADM_WAY_OPTIONS,
  CLINICAL_PATH_OPTIONS,
  CONER_RLTS_OPTIONS,
  DSCG_WAY_OPTIONS,
  GENDER_OPTIONS,
  MEDCAS_QLT_OPTIONS,
  MRG_STAS_OPTIONS,
  NATY_OPTIONS,
  NONE_HAS_OPTIONS,
  NTLY_OPTIONS,
  PAY_WAY_OPTIONS,
  PRFS_OPTIONS,
  RH_OPTIONS,
  TRT_TYPE_OPTIONS,
  YES_NO_12_OPTIONS,
} from '../../mrms.constants';
import { HOMEPAGE_EMPTY_DISPLAY } from '../homepage.constants';
import {
  calcAgeYear,
  calcIptDays,
  syncBirthNativePlaceNames,
  syncCurrAddrText,
} from '../homepage-form.util';

defineOptions({ name: 'HomepagePatientForm' });

interface Props {
  readonly?: boolean;
  /** inpatient=住院信息；appendix=病案附页 */
  part?: 'appendix' | 'inpatient';
  /** 中医信息段：由 loadPage 医院 medins_type 判定 */
  tcmInfoEnabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  readonly: false,
  part: 'inpatient',
  tcmInfoEnabled: false,
});

const form = defineModel<HomepageFormModel>('modelValue', { required: true });

const emit = defineEmits<{
  dirty: [];
}>();

/** 标脏 */
function markDirty() {
  syncCurrAddrText(form.value);
  emit('dirty');
}

/** 出生地/籍贯选区后同步中文地名列并标脏 */
function onBirthNativePlaceChange() {
  syncBirthNativePlaceNames(form.value);
  markDirty();
}

/**
 * Select 空串视为未选，才能显示 placeholder `-`。
 *
 * @param value 表单字段
 */
function emptySelectValue(value?: null | string): string | undefined {
  const text = String(value ?? '').trim();
  if (!text) {
    return undefined;
  }
  return text;
}

/**
 * 附页下拉回写：清空写空串，与表单其它字段口径一致。
 *
 * @param key 表单字段名
 * @param value 选中值或清空
 */
function setEmptySelect(
  key:
    | 'aboCode'
    | 'autopsyCode'
    | 'drugAllergyCode'
    | 'dscgWayCode'
    | 'medcasQltCode'
    | 'rhCode'
    | 'rinp31Flag',
  value: unknown,
) {
  const next = value == null ? '' : String(value);
  form.value[key] = next;
  markDirty();
}

/** 出生/入院变化时回算年龄 */
watch(
  () => [form.value.brdy, form.value.admTime] as const,
  ([brdy, adm]) => {
    if (props.readonly) {
      return;
    }
    const age = calcAgeYear(brdy, adm);
    if (age !== undefined) {
      form.value.ageYear = age;
    }
  },
);

/** 入出院变化回算住院天数 */
watch(
  () => [form.value.admTime, form.value.dscgTime] as const,
  ([adm, dscg]) => {
    if (props.readonly) {
      return;
    }
    const days = calcIptDays(adm, dscg);
    if (days !== undefined) {
      form.value.iptDays = days;
    }
  },
);
</script>

<template>
  <div class="hp-patient-form">
    <!-- 住院信息：无内部小标题 -->
    <div v-if="part === 'inpatient'" class="hp-block">
      <div class="hp-grid">
        <ElFormItem label="付费方式" class="c-5">
          <ElSelect
            v-model="form.payWayCode"
            filterable
            clearable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            :disabled="readonly"
            class="w-full"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in PAY_WAY_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="健康卡号" class="c-3">
          <ElInput
            v-model="form.healthCardNo"
            :disabled="readonly"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="住院次数" class="c-2">
          <ElInputNumber
            v-model="form.visitTimes"
            :min="1"
            :disabled="readonly"
            controls-position="right"
            class="w-full"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="病案号" class="c-2">
          <ElInput
            v-model="form.medcasno"
            :disabled="readonly"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>

        <ElFormItem label="姓名" class="c-2">
          <ElInput
            v-model="form.psnName"
            :disabled="readonly"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="性别" class="c-2">
          <ElSelect
            v-model="form.gendCode"
            clearable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            :disabled="readonly"
            class="w-full"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in GENDER_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="出生日期" class="c-3">
          <ElDatePicker
            v-model="form.brdy"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled="readonly"
            class="w-full"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="年龄" class="c-3">
          <div class="hp-inline">
            <ElInputNumber
              v-model="form.ageYear"
              :min="0"
              :disabled="readonly"
              controls-position="right"
              class="hp-age"
              @change="markDirty"
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            />
            <span class="hp-inline__unit">岁</span>
            <ElInputNumber
              v-model="form.ageMonth"
              :min="0"
              :max="11"
              :disabled="readonly"
              controls-position="right"
              class="hp-age"
              @change="markDirty"
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            />
            <span class="hp-inline__unit">月</span>
          </div>
        </ElFormItem>
        <ElFormItem label="国籍" class="c-2">
          <ElSelect
            v-model="form.ntlyCode"
            filterable
            clearable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            :disabled="readonly"
            class="w-full"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in NTLY_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="民族" class="c-2">
          <ElSelect
            v-model="form.natyCode"
            filterable
            clearable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            :disabled="readonly"
            class="w-full"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in NATY_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="出生地" class="c-4">
          <SelectArea
            v-model="form.birplcCodes"
            size="small"
            :max-level="3"
            :disabled="readonly"
            class="w-full"
            @change="onBirthNativePlaceChange"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="籍贯" class="c-3">
          <SelectArea
            v-model="form.naplCodes"
            size="small"
            :max-level="2"
            :disabled="readonly"
            class="w-full"
            @change="onBirthNativePlaceChange"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="婚姻" class="c-3">
          <ElSelect
            v-model="form.mrgStasCode"
            clearable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            :disabled="readonly"
            class="w-full"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in MRG_STAS_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="身份证号" class="c-4">
          <ElInput
            v-model="form.certno"
            :disabled="readonly"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="职业" class="c-5">
          <ElSelect
            v-model="form.prfsCode"
            filterable
            clearable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            :disabled="readonly"
            class="w-full"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in PRFS_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="本人电话" class="c-3">
          <ElInput
            v-model="form.psnTel"
            :disabled="readonly"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>

        <ElFormItem label="现住址" class="c-4">
          <SelectArea
            v-model="form.currAddrCodes"
            size="small"
            :max-level="3"
            :disabled="readonly"
            class="w-full"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="详细地址" class="c-5">
          <ElInput
            v-model="form.currAddrDetail"
            :disabled="readonly"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="邮编" class="c-3">
          <ElInput
            v-model="form.currAddrPoscode"
            :disabled="readonly"
            class="ctrl-xs"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>

        <ElFormItem label="户口地址" class="c-4">
          <SelectArea
            v-model="form.resdAddrCodes"
            size="small"
            :max-level="3"
            :disabled="readonly"
            class="w-full"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="详细地址" class="c-5">
          <ElInput
            v-model="form.resdAddrDetail"
            :disabled="readonly"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="邮编" class="c-3">
          <ElInput
            v-model="form.resdAddrPoscode"
            :disabled="readonly"
            class="ctrl-xs"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>

        <ElFormItem label="工作单位" class="c-6">
          <ElInput
            v-model="form.emprAddr"
            :disabled="readonly"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="单位电话" class="c-3">
          <ElInput
            v-model="form.emprTel"
            :disabled="readonly"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="单位邮编" class="c-3">
          <ElInput
            v-model="form.emprPoscode"
            :disabled="readonly"
            class="ctrl-xs"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>

        <ElFormItem label="联系人" class="c-2">
          <ElInput
            v-model="form.conerName"
            :disabled="readonly"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="关系" class="c-2">
          <ElSelect
            v-model="form.conerRltsCode"
            clearable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            :disabled="readonly"
            class="w-full"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in CONER_RLTS_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="联系地址" class="c-5">
          <ElInput
            v-model="form.conerAddr"
            :disabled="readonly"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="联系电话" class="c-3">
          <ElInput
            v-model="form.conerTel"
            :disabled="readonly"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>

        <!-- 中医信息：医院 medins_type 为中医时由 loadPage.tcmInfoEnabled 打开；无系统默认值 -->
        <ElFormItem v-if="props.tcmInfoEnabled" label="中医信息" class="c-12">
          <div class="hp-tcm">
            <label class="hp-tcm__item">
              <span class="hp-tcm__label">实施临床路径</span>
              <ElSelect
                v-model="form.clinicalPathCode"
                clearable
                :placeholder="HOMEPAGE_EMPTY_DISPLAY"
                :disabled="readonly"
                class="hp-tcm__select"
                @change="markDirty"
              >
                <ElOption
                  v-for="opt in CLINICAL_PATH_OPTIONS"
                  :key="String(opt.value)"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </label>
            <label class="hp-tcm__item">
              <span class="hp-tcm__label">使用医疗机构中药制剂</span>
              <ElSelect
                v-model="form.tcmPrepCode"
                clearable
                :placeholder="HOMEPAGE_EMPTY_DISPLAY"
                :disabled="readonly"
                class="hp-tcm__select"
                @change="markDirty"
              >
                <ElOption
                  v-for="opt in YES_NO_12_OPTIONS"
                  :key="String(opt.value)"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </label>
            <label class="hp-tcm__item">
              <span class="hp-tcm__label">使用中医诊疗设备</span>
              <ElSelect
                v-model="form.tcmDeviceCode"
                clearable
                :placeholder="HOMEPAGE_EMPTY_DISPLAY"
                :disabled="readonly"
                class="hp-tcm__select"
                @change="markDirty"
              >
                <ElOption
                  v-for="opt in YES_NO_12_OPTIONS"
                  :key="String(opt.value)"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </label>
            <label class="hp-tcm__item">
              <span class="hp-tcm__label">使用中医诊疗技术</span>
              <ElSelect
                v-model="form.tcmTechCode"
                clearable
                :placeholder="HOMEPAGE_EMPTY_DISPLAY"
                :disabled="readonly"
                class="hp-tcm__select"
                @change="markDirty"
              >
                <ElOption
                  v-for="opt in YES_NO_12_OPTIONS"
                  :key="String(opt.value)"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </label>
            <label class="hp-tcm__item">
              <span class="hp-tcm__label">辨证施护</span>
              <ElSelect
                v-model="form.syndromeNurseCode"
                clearable
                :placeholder="HOMEPAGE_EMPTY_DISPLAY"
                :disabled="readonly"
                class="hp-tcm__select"
                @change="markDirty"
              >
                <ElOption
                  v-for="opt in YES_NO_12_OPTIONS"
                  :key="String(opt.value)"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </label>
          </div>
        </ElFormItem>

        <ElFormItem label="入院途径" class="c-2">
          <ElSelect
            v-model="form.admWayCode"
            clearable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            :disabled="readonly"
            class="w-full"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in ADM_WAY_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="治疗类别" class="c-2">
          <ElSelect
            v-model="form.trtTypeCode"
            clearable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            :disabled="readonly"
            class="w-full"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in TRT_TYPE_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="住院号" class="c-2">
          <ElInput
            v-model="form.iptNo"
            :disabled="readonly"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="住院天数" class="c-2">
          <div class="hp-inline">
            <ElInputNumber
              v-model="form.iptDays"
              :min="0"
              :disabled="readonly"
              controls-position="right"
              class="hp-age"
              @change="markDirty"
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            />
            <span class="hp-inline__unit">天</span>
          </div>
        </ElFormItem>
        <ElFormItem label="入院时间" class="c-4">
          <ElDatePicker
            v-model="form.admTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled="readonly"
            class="w-full"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>

        <ElFormItem label="入院科室" class="c-3">
          <ElInput
            v-model="form.admCatyName"
            :disabled="readonly"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="病区" class="c-2">
          <ElInput
            v-model="form.admWard"
            :disabled="readonly"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="床位" class="c-2">
          <ElInput
            v-model="form.admBed"
            :disabled="readonly"
            class="ctrl-xs"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="出院时间" class="c-4">
          <ElDatePicker
            v-model="form.dscgTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled="readonly"
            class="w-full"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="出院科室" class="c-3">
          <ElInput
            v-model="form.dscgCatyName"
            :disabled="readonly"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="病区" class="c-2">
          <ElInput
            v-model="form.dscgWard"
            :disabled="readonly"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="床位" class="c-2">
          <ElInput
            v-model="form.dscgBed"
            :disabled="readonly"
            class="ctrl-xs"
            @change="markDirty"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </ElFormItem>
        <ElFormItem label="转科科别" class="c-5">
          <ElInput
            v-model="form.transferDeptSummary"
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>

        <ElFormItem label="护理天数" class="c-12">
          <div class="hp-nurse-days">
            <span>特级</span>
            <ElInputNumber
              v-model="form.spgaNurscareDays"
              :min="0"
              :disabled="readonly"
              controls-position="right"
              @change="markDirty"
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            />
            <span>一级</span>
            <ElInputNumber
              v-model="form.lv1NurscareDays"
              :min="0"
              :disabled="readonly"
              controls-position="right"
              @change="markDirty"
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            />
            <span>二级</span>
            <ElInputNumber
              v-model="form.scdNurscareDays"
              :min="0"
              :disabled="readonly"
              controls-position="right"
              @change="markDirty"
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            />
            <span>三级</span>
            <ElInputNumber
              v-model="form.lv3NurscareDays"
              :min="0"
              :disabled="readonly"
              controls-position="right"
              @change="markDirty"
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            />
          </div>
        </ElFormItem>
      </div>
    </div>

    <!-- 病案附页：空值统一 `-`（Radio 改 Select，避免未选时空白；空串 Select 用 undefined 才能出 placeholder） -->
    <div v-else class="hp-block hp-block--appendix">
      <div class="hp-grid">
        <ElFormItem label="药物过敏" class="c-3">
          <ElSelect
            :model-value="emptySelectValue(form.drugAllergyCode)"
            clearable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            :disabled="readonly"
            class="w-full"
            @change="(v) => setEmptySelect('drugAllergyCode', v)"
          >
            <ElOption
              v-for="opt in NONE_HAS_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="过敏药物" class="c-4">
          <ElInput
            v-model="form.allergyDrugName"
            :disabled="readonly || form.drugAllergyCode !== '2'"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
        <ElFormItem label="入院前昏迷" class="c-3">
          <ElInput
            v-model="form.brnDamgBfadmComaDura"
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
        <ElFormItem label="入院后昏迷" class="c-3">
          <ElInput
            v-model="form.brnDamgAfadmComaDura"
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
        <ElFormItem label="呼吸机时长" class="c-3">
          <ElInput
            v-model="form.ventUsedDura"
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
        <ElFormItem label="死亡尸检" class="c-3">
          <ElSelect
            :model-value="emptySelectValue(form.autopsyCode)"
            clearable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            :disabled="readonly"
            class="w-full"
            @change="(v) => setEmptySelect('autopsyCode', v)"
          >
            <ElOption
              v-for="opt in YES_NO_12_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="血型" class="c-2">
          <ElSelect
            :model-value="emptySelectValue(form.aboCode)"
            clearable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            :disabled="readonly"
            class="w-full"
            @change="(v) => setEmptySelect('aboCode', v)"
          >
            <ElOption
              v-for="opt in ABO_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="Rh" class="c-2">
          <ElSelect
            :model-value="emptySelectValue(form.rhCode)"
            clearable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            :disabled="readonly"
            class="w-full"
            @change="(v) => setEmptySelect('rhCode', v)"
          >
            <ElOption
              v-for="opt in RH_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="科主任" class="c-3">
          <ElInput
            v-model="form.deptDirectorName"
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
        <ElFormItem label="主任医师" class="c-3">
          <ElInput
            v-model="form.chfdrName"
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
        <ElFormItem label="主治医师" class="c-3">
          <ElInput
            v-model="form.atddrName"
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
        <ElFormItem label="住院医师" class="c-3">
          <ElInput
            v-model="form.iptDrName"
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
        <ElFormItem label="责任护士" class="c-3">
          <ElInput
            v-model="form.respNursName"
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
        <ElFormItem label="进修医师" class="c-3">
          <ElInput
            v-model="form.trainDrName"
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
        <ElFormItem label="实习医师" class="c-3">
          <ElInput
            v-model="form.intnDrName"
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
        <ElFormItem label="编码员" class="c-3">
          <ElInput
            v-model="form.codrName"
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>

        <ElFormItem label="病案质量" class="c-3">
          <ElSelect
            :model-value="emptySelectValue(form.medcasQltCode)"
            clearable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            :disabled="readonly"
            class="w-full"
            @change="(v) => setEmptySelect('medcasQltCode', v)"
          >
            <ElOption
              v-for="opt in MEDCAS_QLT_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="质控日期" class="c-3">
          <ElDatePicker
            v-model="form.qltctrlDate"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled="readonly"
            class="w-full"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
        <ElFormItem label="质控医师" class="c-3">
          <ElInput
            v-model="form.qltctrlDrName"
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
        <ElFormItem label="质控护士" class="c-3">
          <ElInput
            v-model="form.qltctrlNursName"
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
        <ElFormItem label="离院方式" class="c-4">
          <ElSelect
            :model-value="emptySelectValue(form.dscgWayCode)"
            clearable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            :disabled="readonly"
            class="w-full"
            @change="(v) => setEmptySelect('dscgWayCode', v)"
          >
            <ElOption
              v-for="opt in DSCG_WAY_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="接收医院" class="c-4">
          <ElInput
            v-model="form.acpMedinsName"
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
        <ElFormItem label="接收社区" class="c-4">
          <ElInput
            v-model="form.acpCommunityName"
            :disabled="readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
        <ElFormItem label="31天再住院" class="c-3">
          <ElSelect
            :model-value="emptySelectValue(form.rinp31Flag)"
            clearable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            :disabled="readonly"
            class="w-full"
            @change="(v) => setEmptySelect('rinp31Flag', v)"
          >
            <ElOption
              v-for="opt in NONE_HAS_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="再住院目的" class="c-9">
          <ElInput
            v-model="form.rinp31Purpose"
            :disabled="readonly || form.rinp31Flag !== '2'"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </ElFormItem>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hp-patient-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.hp-block {
  padding: 6px 10px 4px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: 6px;
  box-shadow: 0 1px 0 rgb(0 0 0 / 2%);
}

/* 12 列：短字段 c-2、中等 c-3/c-4、长文本 c-5+ */
.hp-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  column-gap: 8px;
}

.hp-grid > :deep(.el-form-item) {
  margin-bottom: 6px;
}

.hp-grid > :deep(.c-2) {
  grid-column: span 2;
}

.hp-grid > :deep(.c-3) {
  grid-column: span 3;
}

.hp-grid > :deep(.c-4) {
  grid-column: span 4;
}

.hp-grid > :deep(.c-5) {
  grid-column: span 5;
}

.hp-grid > :deep(.c-6) {
  grid-column: span 6;
}

.hp-grid > :deep(.c-9) {
  grid-column: span 9;
}

.hp-grid > :deep(.c-12) {
  grid-column: span 12;
}

/* 邮编/床位等极短内容：控件本身也限宽，避免格子内空白晃眼 */
.ctrl-xs {
  max-width: 120px;
}

.hp-age {
  width: 88px !important;
}

.hp-tcm {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 4px 12px;
  align-items: center;
  min-width: 0;
}

.hp-tcm__item {
  display: inline-flex;
  flex: 0 0 auto;
  gap: 6px;
  align-items: center;
  margin: 0;
}

.hp-tcm__label {
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 28px;
  white-space: nowrap;
}

.hp-tcm__select {
  width: 88px;
}

.hp-inline {
  display: flex;
  gap: 4px;
  align-items: center;
  width: 100%;
}

.hp-inline__unit {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.hp-nurse-days {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  align-items: center;
}

.hp-nurse-days span {
  color: var(--el-text-color-regular);
  font-size: 12px;
}

.hp-nurse-days :deep(.el-input-number) {
  width: 88px;
}

.w-full {
  width: 100%;
}

:deep(.el-form-item__label) {
  height: 28px;
  padding-right: 6px !important;
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 28px;
}

:deep(.el-form-item__content) {
  line-height: 28px;
}

:deep(.el-radio) {
  margin-right: 8px;
  height: 28px;
}

@media (max-width: 1280px) {
  .hp-grid {
    grid-template-columns: repeat(8, minmax(0, 1fr));
  }

  .hp-grid > :deep(.c-2) {
    grid-column: span 2;
  }

  .hp-grid > :deep(.c-3),
  .hp-grid > :deep(.c-4) {
    grid-column: span 4;
  }

  .hp-grid > :deep(.c-5),
  .hp-grid > :deep(.c-6),
  .hp-grid > :deep(.c-9),
  .hp-grid > :deep(.c-12) {
    grid-column: span 8;
  }
}

@media (max-width: 900px) {
  .hp-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .hp-grid > :deep(.c-2),
  .hp-grid > :deep(.c-3),
  .hp-grid > :deep(.c-4),
  .hp-grid > :deep(.c-5),
  .hp-grid > :deep(.c-6),
  .hp-grid > :deep(.c-9),
  .hp-grid > :deep(.c-12) {
    grid-column: span 4;
  }

  .ctrl-xs {
    max-width: none;
  }
}

/* 禁用态也要能看清空值占位 `-`（过敏药物/再住院目的等） */
.hp-block--appendix :deep(.el-input.is-disabled .el-input__inner::placeholder),
.hp-block--appendix :deep(.el-input.is-disabled .el-input__wrapper .el-input__inner::placeholder) {
  color: var(--el-text-color-placeholder);
  -webkit-text-fill-color: var(--el-text-color-placeholder);
  opacity: 1;
}
</style>
