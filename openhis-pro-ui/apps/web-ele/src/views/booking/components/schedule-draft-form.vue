<script setup lang="ts">
import type { BookingEntityId } from '#/api/booking/booking-id';
import { bookingOptionValue, isSameBookingId, toBookingId } from '#/api/booking/booking-id';
import type { BookingOptionDto } from '#/api/booking/booking-types';
import type { SlotProvisionTemplateItemDto } from '#/api/booking/schedule-template';
import { loadSlotProvisionTemplateApi } from '#/api/booking/schedule-template';

import { computed, ref, watch } from 'vue';

import { isPractitionerScheduleMode } from '#/views/booking/components/schedule-draft-form.utils';
import SlotTemplateRowsField from '#/views/booking/components/slot-template-rows-field.vue';
import { SCHEDULE_MODE_OPTIONS } from '#/views/booking/components/schedule-template-shared';
import {
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';

defineOptions({ name: 'ScheduleDraftForm' });

export interface ScheduleDraftFormModel {
  name: string;
  scheduleMode: 'MODE-A' | 'MODE-B';
  practitionerId?: BookingEntityId;
  healthcareServiceId?: BookingEntityId;
  /** 出诊日期 YYYY-MM-DD */
  planningDate: string;
  /** 内部：保存时拼 planningHorizon */
  startTime: string;
  endTime: string;
  /** 可选：关联 T1 放号时段模板，选中后只读展示时段行 */
  slotProvisionTemplateId?: BookingEntityId;
}

const props = withDefaults(
  defineProps<{
    organizationId?: BookingEntityId;
    practitioners?: BookingOptionDto[];
    healthcareServices?: BookingOptionDto[];
    slotProvisionTemplateOptions?: BookingOptionDto[];
  }>(),
  {
    practitioners: () => [],
    healthcareServices: () => [],
    slotProvisionTemplateOptions: () => [],
  },
);

const emit = defineEmits<{
  /** 号别类型或医师变更，父级重载号源/放号时段下拉 */
  'reload-context': [payload: { practitionerId?: BookingEntityId }];
}>();

const model = defineModel<ScheduleDraftFormModel>({ required: true });

const isPractitionerSchedule = computed(() => isPractitionerScheduleMode(model.value.scheduleMode));

const practitionerSelected = computed(
  () => isPractitionerSchedule.value && Boolean(toBookingId(model.value.practitionerId)),
);

/** 选中放号时段后只读展示的行（不在此维护） */
const slotPreviewRows = ref<SlotProvisionTemplateItemDto[]>([]);

/** 加载 T1 时段行用于只读预览 */
async function loadSlotPreviewRows(templateId?: BookingEntityId) {
  const resolvedId = toBookingId(templateId);
  if (!resolvedId) {
    slotPreviewRows.value = [];
    return;
  }
  try {
    const detail = await loadSlotProvisionTemplateApi({ id: resolvedId });
    slotPreviewRows.value = detail?.slotTemplates ?? [];
  } catch (error) {
    slotPreviewRows.value = [];
    ElMessage.error((error as Error).message || '加载放号时段失败');
  }
}

async function handleT1Change(templateId?: BookingEntityId) {
  await loadSlotPreviewRows(templateId);
}

watch(
  () => model.value.slotProvisionTemplateId,
  (templateId) => {
    void loadSlotPreviewRows(templateId);
  },
  { immediate: true },
);

watch(
  () => model.value.scheduleMode,
  (mode, previous) => {
    if (previous === undefined) {
      return;
    }
    if (mode === 'MODE-B') {
      model.value.practitionerId = undefined;
    }
    model.value.healthcareServiceId = undefined;
    emit('reload-context', { practitionerId: undefined });
  },
);

/** 医师号切换出诊医师时，通知父级按可接诊号源收窄下拉（含首次选择，避免仍见全科室号源） */
watch(
  () => model.value.practitionerId,
  (practitionerId, previous) => {
    if (model.value.scheduleMode !== 'MODE-A') {
      return;
    }
    if (isSameBookingId(practitionerId, previous)) {
      return;
    }
    model.value.healthcareServiceId = undefined;
    emit('reload-context', {
      practitionerId: toBookingId(practitionerId) || undefined,
    });
  },
);
</script>

<template>
  <ElForm label-width="100px">
    <ElFormItem label="名称">
      <ElInput v-model="model.name" placeholder="如：张医生专家号" />
    </ElFormItem>
    <ElFormItem label="号别类型" required>
      <ElSelect v-model="model.scheduleMode" style="width: 100%">
        <ElOption
          v-for="item in SCHEDULE_MODE_OPTIONS"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </ElSelect>
      <p class="schedule-draft-form__hint">医师号：指名到医生；科室号：只排到科室，到院再分诊</p>
    </ElFormItem>
    <ElFormItem v-if="isPractitionerSchedule" label="出诊医师" required>
      <ElSelect
        v-model="model.practitionerId"
        filterable
        placeholder="选择医师"
        style="width: 100%"
      >
        <ElOption
          v-for="item in practitioners"
          :key="item.value"
          :label="item.label"
          :value="bookingOptionValue(item.value)"
        />
      </ElSelect>
      <p class="schedule-draft-form__hint">
        先选医师，号源类型将根据该医师在用户管理中配置的可接诊号源显示
      </p>
    </ElFormItem>
    <ElFormItem label="号源类型" required>
      <ElSelect
        v-model="model.healthcareServiceId"
        filterable
        :disabled="isPractitionerSchedule && !practitionerSelected"
        :placeholder="
          isPractitionerSchedule && !practitionerSelected ? '请先选择出诊医师' : '请选择'
        "
        style="width: 100%"
      >
        <ElOption
          v-for="item in healthcareServices"
          :key="item.value"
          :label="item.label"
          :value="bookingOptionValue(item.value)"
        />
      </ElSelect>
      <p
        v-if="isPractitionerSchedule && practitionerSelected && !healthcareServices.length"
        class="schedule-draft-form__hint schedule-draft-form__hint--warn"
      >
        该医师未配置可接诊号源，请先在用户管理中维护
      </p>
    </ElFormItem>
    <ElFormItem label="放号时段">
      <ElSelect
        v-model="model.slotProvisionTemplateId"
        clearable
        filterable
        placeholder="可选，选中后预览时段；具体放号在发布后批量放号"
        style="width: 100%"
        @change="handleT1Change"
      >
        <ElOption
          v-for="item in slotProvisionTemplateOptions"
          :key="item.value"
          :label="item.label"
          :value="bookingOptionValue(item.value)"
        />
      </ElSelect>
      <p class="schedule-draft-form__hint">
        时段明细在「批量放号」中导入或手动维护；此处仅绑定模板供预览
      </p>
    </ElFormItem>
    <SlotTemplateRowsField
      v-if="model.slotProvisionTemplateId && slotPreviewRows.length"
      v-model="slotPreviewRows"
      readonly
      label="时段"
    />
    <ElFormItem label="出诊日期" required>
      <ElDatePicker
        v-model="model.planningDate"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="选择日期"
        style="width: 100%"
      />
    </ElFormItem>
  </ElForm>
</template>

<style scoped>
.schedule-draft-form__hint {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.schedule-draft-form__hint--warn {
  color: var(--el-color-warning);
}
</style>
