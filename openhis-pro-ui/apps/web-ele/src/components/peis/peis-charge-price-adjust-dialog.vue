<script setup lang="ts">
import type { PhysicalExamChargeItemRowDto } from '#/api/peis/physical-exam-charge';

import { computed, reactive, ref, watch } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';

import { httpAdjustPhysicalExamChargePrices } from '#/api/peis/physical-exam-charge';
import { extractRequestErrorMessage } from '#/utils/http/stale-cross-role-error';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

/** 个检改价原因：目录议价 + 套餐议价。 */
const OVERRIDE_REASON_OPTIONS = [
  { value: 'PEIS_MANUAL_DISCOUNT', label: '优惠减免' },
  { value: 'PEIS_MANUAL_NEGOTIATE', label: '议价协商' },
  { value: 'PEIS_PACKAGE_PRICE', label: '套餐议价' },
  { value: 'PEIS_MANUAL_OTHER', label: '其他' },
] as const;

const props = defineProps<{
  encounterId: string;
  row: PhysicalExamChargeItemRowDto | null;
}>();

const emit = defineEmits<{
  success: [];
}>();

const visible = defineModel<boolean>('visible', { default: false });

const loading = ref(false);
const form = reactive({
  unitPrice: 0,
  overrideReasonCode: 'PEIS_MANUAL_DISCOUNT',
  overrideReasonText: '',
});

const catalogPrice = computed(() => props.row?.catalogUnitPrice ?? props.row?.unitPrice);

watch(
  () => [visible.value, props.row] as const,
  ([open, row]) => {
    // 关闭或无行时不覆盖正在编辑的表单
    if (!open || !row) {
      return;
    }
    form.unitPrice = Number(row.unitPrice ?? 0);
    form.overrideReasonCode = row.overrideReasonCode || 'PEIS_MANUAL_DISCOUNT';
    form.overrideReasonText = '';
  },
);

/**
 * 提交单项改价；入账以后端 ChargeItem 为准。
 */
async function submit() {
  if (!props.encounterId || !props.row?.chargeItemId) {
    return;
  }
  if (form.unitPrice < 0) {
    ElMessage.warning('单价不能为负');
    return;
  }
  if (form.overrideReasonCode === 'PEIS_MANUAL_OTHER' && !form.overrideReasonText.trim()) {
    ElMessage.warning('请填写改价原因说明');
    return;
  }
  loading.value = true;
  try {
    await httpAdjustPhysicalExamChargePrices({
      encounterId: props.encounterId,
      items: [
        {
          chargeItemId: String(props.row.chargeItemId),
          unitPrice: form.unitPrice,
          overrideReasonCode: form.overrideReasonCode,
          overrideReasonText: form.overrideReasonText.trim() || undefined,
        },
      ],
    });
    ElMessage.success('改价成功，请重新预结算后再收费');
    visible.value = false;
    emit('success');
  } catch (error) {
    ElMessage.error(extractRequestErrorMessage(error, '改价失败'));
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <ElDialog
    v-model="visible"
    title="体检项目改价"
    width="440px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div v-if="row" class="space-y-3">
      <div class="text-sm text-[var(--el-text-color-regular)]">
        项目：<strong>{{ row.itemLabel || '—' }}</strong>
      </div>
      <div class="text-sm text-[var(--el-text-color-secondary)]">
        目录价：¥{{ formatMoneyDisplay(catalogPrice) }}
      </div>
      <ElForm label-width="88px">
        <ElFormItem label="新单价" required>
          <ElInputNumber
            v-model="form.unitPrice"
            :min="0"
            :precision="2"
            :step="1"
            class="!w-full"
          />
        </ElFormItem>
        <ElFormItem label="改价原因" required>
          <ElSelect v-model="form.overrideReasonCode" class="w-full">
            <ElOption
              v-for="opt in OVERRIDE_REASON_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem
          v-if="form.overrideReasonCode === 'PEIS_MANUAL_OTHER'"
          label="原因说明"
          required
        >
          <ElInput
            v-model="form.overrideReasonText"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="请填写改价原因"
          />
        </ElFormItem>
      </ElForm>
    </div>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :loading="loading" @click="submit">确认改价</ElButton>
    </template>
  </ElDialog>
</template>
