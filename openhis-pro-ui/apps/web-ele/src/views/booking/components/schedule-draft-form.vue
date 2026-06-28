<script setup lang="ts">
import type { BookingEntityId } from '#/api/booking/booking-id';
import { bookingOptionValue } from '#/api/booking/booking-id';
import type { BookingOptionDto } from '#/api/booking/booking-types';
import { loadSlotProvisionTemplateApi } from '#/api/booking/schedule-template';

import { computed } from 'vue';

import {
  applySessionPreset,
  applyT1ItemsToForm,
  SESSION_PRESETS,
  type SessionPreset,
} from '#/views/booking/components/schedule-draft-form.utils';
import {
  ElButton,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTimePicker,
} from 'element-plus';

export interface ScheduleDraftFormModel {
  name: string;
  practitionerId?: BookingEntityId;
  locationId?: BookingEntityId;
  healthcareServiceId?: BookingEntityId;
  /** 出诊日期 YYYY-MM-DD */
  planningDate: string;
  /** 开始时刻 HH:mm:ss */
  startTime: string;
  /** 结束时刻 HH:mm:ss */
  endTime: string;
  /** 关联 T1 放号时段模板 */
  slotProvisionTemplateId?: BookingEntityId;
}

defineProps<{
  practitioners?: BookingOptionDto[];
  clinicLocations?: BookingOptionDto[];
  healthcareServices?: BookingOptionDto[];
  slotProvisionTemplateOptions?: BookingOptionDto[];
}>();

const model = defineModel<ScheduleDraftFormModel>({ required: true });

const activePreset = computed<SessionPreset | null>(() => {
  for (const key of Object.keys(SESSION_PRESETS) as SessionPreset[]) {
    const preset = SESSION_PRESETS[key];
    if (model.value.startTime === preset.startTime && model.value.endTime === preset.endTime) {
      return key;
    }
  }
  return null;
});

function handlePreset(preset: SessionPreset) {
  applySessionPreset(model.value, preset);
}

async function handleT1Change(templateId?: BookingEntityId) {
  if (!templateId) {
    return;
  }
  try {
    const detail = await loadSlotProvisionTemplateApi({ id: templateId });
    applyT1ItemsToForm(model.value, detail?.slotTemplates);
  } catch (error) {
    ElMessage.error((error as Error).message || '加载放号时段失败，请刷新页面后重试');
  }
}
</script>

<template>
  <ElForm label-width="88px">
    <ElFormItem label="名称">
      <ElInput v-model="model.name" placeholder="如：内科上午" />
    </ElFormItem>
    <ElFormItem label="出诊医师">
      <ElSelect
        v-model="model.practitionerId"
        clearable
        filterable
        placeholder="选择医师"
        style="width: 100%"
      >
        <ElOption
          v-for="item in practitioners || []"
          :key="item.value"
          :label="item.label"
          :value="bookingOptionValue(item.value)"
        />
      </ElSelect>
    </ElFormItem>
    <ElFormItem label="诊室">
      <ElSelect
        v-model="model.locationId"
        clearable
        filterable
        placeholder="可选，到院分诊时再定"
        style="width: 100%"
      >
        <ElOption
          v-for="item in clinicLocations || []"
          :key="item.value"
          :label="item.label"
          :value="bookingOptionValue(item.value)"
        />
      </ElSelect>
    </ElFormItem>
    <ElFormItem label="号源类型" required>
      <ElSelect
        v-model="model.healthcareServiceId"
        clearable
        filterable
        placeholder="必选，如专家号/普通号"
        style="width: 100%"
      >
        <ElOption
          v-for="item in healthcareServices || []"
          :key="item.value"
          :label="item.label"
          :value="bookingOptionValue(item.value)"
        />
      </ElSelect>
    </ElFormItem>
    <ElFormItem label="放号时段">
      <ElSelect
        v-model="model.slotProvisionTemplateId"
        clearable
        filterable
        :placeholder="
          slotProvisionTemplateOptions?.length
            ? '可选，选中后自动带出出诊时段'
            : '请先在「排班设置」配置放号时段'
        "
        style="width: 100%"
        @change="handleT1Change"
      >
        <ElOption
          v-for="item in slotProvisionTemplateOptions || []"
          :key="item.value"
          :label="item.label"
          :value="bookingOptionValue(item.value)"
        />
      </ElSelect>
    </ElFormItem>
    <ElFormItem label="出诊日期" required>
      <ElDatePicker
        v-model="model.planningDate"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="选择日期"
        style="width: 100%"
      />
    </ElFormItem>
    <ElFormItem label="出诊时段" required>
      <div class="schedule-draft-form__presets">
        <ElButton
          v-for="(preset, key) in SESSION_PRESETS"
          :key="key"
          size="small"
          :type="activePreset === key ? 'primary' : 'default'"
          @click="handlePreset(key as SessionPreset)"
        >
          {{ preset.label }}
        </ElButton>
      </div>
      <div class="schedule-draft-form__time-range">
        <ElTimePicker
          v-model="model.startTime"
          placeholder="开始"
          value-format="HH:mm:ss"
          format="HH:mm"
          style="width: 128px"
        />
        <span class="schedule-draft-form__time-sep">至</span>
        <ElTimePicker
          v-model="model.endTime"
          placeholder="结束"
          value-format="HH:mm:ss"
          format="HH:mm"
          style="width: 128px"
        />
      </div>
    </ElFormItem>
  </ElForm>
</template>

<style scoped>
.schedule-draft-form__presets {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.schedule-draft-form__time-range {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.schedule-draft-form__time-sep {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
