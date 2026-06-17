<script setup lang="ts">
import type { SlotTemplateRequest } from '#/api/booking/outpatient-schedule';

import { reactive, watch } from 'vue';

import { provisionSlotBatchApi } from '#/api/booking/outpatient-schedule';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElMessage,
} from 'element-plus';

const props = defineProps<{
  visible: boolean;
  organizationId?: number;
  scheduleId?: number;
}>();

const emit = defineEmits<{
  'update:visible': [value: boolean];
  done: [];
}>();

const form = reactive({
  startTime: '',
  endTime: '',
  capacity: 10,
});

watch(
  () => props.visible,
  (open) => {
    if (!open) return;
    form.capacity = 10;
  },
);

const { run: runProvision, loading } = useRequest(
  async () => {
    if (!props.organizationId || !props.scheduleId) {
      throw new Error('缺少机构或日程');
    }
    if (!form.startTime || !form.endTime) {
      throw new Error('请填写时段起止');
    }
    const slotTemplates: SlotTemplateRequest[] = [
      {
        startTime: form.startTime,
        endTime: form.endTime,
        capacity: form.capacity,
      },
    ];
    return provisionSlotBatchApi({
      scheduleId: props.scheduleId,
      organizationId: props.organizationId,
      slotTemplates,
    });
  },
  {
    manual: true,
    onSuccess: (res) => {
      ElMessage.success(`已生成 ${res?.generatedCount ?? 0} 个号源`);
      emit('done');
      emit('update:visible', false);
    },
  },
);

async function handleSubmit() {
  try {
    await runProvision();
  } catch (error) {
    ElMessage.error((error as Error).message || '放号失败');
  }
}

function fillMorningTemplate() {
  const today = new Date().toISOString().slice(0, 10);
  form.startTime = `${today}T08:00:00`;
  form.endTime = `${today}T08:30:00`;
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    title="批量放号"
    width="480px"
    @update:model-value="emit('update:visible', $event)"
  >
    <ElForm label-width="88px">
      <ElFormItem label="开始">
        <ElDatePicker
          v-model="form.startTime"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 100%"
        />
      </ElFormItem>
      <ElFormItem label="结束">
        <ElDatePicker
          v-model="form.endTime"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 100%"
        />
      </ElFormItem>
      <ElFormItem label="容量">
        <ElInputNumber v-model="form.capacity" :min="1" :max="999" />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="fillMorningTemplate">填入示例时段</ElButton>
      <ElButton @click="emit('update:visible', false)">取消</ElButton>
      <ElButton type="primary" :loading="loading" @click="handleSubmit">确认放号</ElButton>
    </template>
  </ElDialog>
</template>
