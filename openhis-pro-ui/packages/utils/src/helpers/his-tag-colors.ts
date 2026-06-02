/**
 * HIS 标签颜色工具函数
 * 用于获取医院信息系统中各种标签的背景色和文字颜色
 */

// 住院状态映射配置（支持中文名称和英文 code）
const INPATIENT_STATUS_MAP: Record<string, { bg: string; text: string }> = {
  // 中文名称映射
  待登记: { bg: 'his-inpatient-to-be-registered', text: 'his-tag-text-white' },
  待入科: { bg: 'his-inpatient-registered', text: 'his-tag-text-white' },
  待出院: { bg: 'his-inpatient-awaiting-discharge', text: 'his-tag-text-white' },
  待出院结算: { bg: 'his-inpatient-discharged-from-hospital', text: 'his-tag-text-white' },
  已入院: { bg: 'his-inpatient-admitted-to-the-hospital', text: 'his-tag-text-white' },
  已入科: { bg: 'his-inpatient-admitted-to-the-hospital', text: 'his-tag-text-white' },
  在院: { bg: 'his-inpatient-admitted-to-the-hospital', text: 'his-tag-text-white' },
  待转科: { bg: 'his-inpatient-pending-transfer', text: 'his-tag-text-white' },
  已结算出院: { bg: 'his-inpatient-already-settled', text: 'his-tag-text-white' },
  取消入科: { bg: 'his-inpatient-cancel-registration', text: 'his-tag-text-white' },
  // 英文 code 映射
  'to-be-registered': { bg: 'his-inpatient-to-be-registered', text: 'his-tag-text-white' },
  registered: { bg: 'his-inpatient-registered', text: 'his-tag-text-white' },
  'awaiting-discharge': { bg: 'his-inpatient-awaiting-discharge', text: 'his-tag-text-white' },
  'discharged-from-hospital': {
    bg: 'his-inpatient-discharged-from-hospital',
    text: 'his-tag-text-white',
  },
  'admitted-to-the-hospital': {
    bg: 'his-inpatient-admitted-to-the-hospital',
    text: 'his-tag-text-white',
  },
  ADMITTED: { bg: 'his-inpatient-admitted-to-the-hospital', text: 'his-tag-text-white' },
  IN_PROGRESS: { bg: 'his-inpatient-admitted-to-the-hospital', text: 'his-tag-text-white' },
  'pending-transfer': { bg: 'his-inpatient-pending-transfer', text: 'his-tag-text-white' },
  'already-settled': { bg: 'his-inpatient-already-settled', text: 'his-tag-text-white' },
  'cancel-registration': { bg: 'his-inpatient-cancel-registration', text: 'his-tag-text-white' },
};

// 门诊状态映射配置（支持中文名称和英文 code）
const OUTPATIENT_STATUS_MAP: Record<string, { bg: string; text: string }> = {
  // 中文名称映射
  待诊: { bg: 'his-outpatient-planned', text: 'his-tag-text-white' },
  在诊: { bg: 'his-outpatient-in-progress', text: 'his-tag-text-white' },
  暂离: { bg: 'his-outpatient-on-hold', text: 'his-tag-text-white' },
  诊毕: { bg: 'his-outpatient-discharged', text: 'his-tag-text-white' },
  完成: { bg: 'his-outpatient-completed', text: 'his-tag-text-white' },
  退号: { bg: 'his-outpatient-cancelled', text: 'his-tag-text-white' },
  已中断: { bg: 'his-outpatient-discontinued', text: 'his-tag-text-white' },
  错误: { bg: 'his-outpatient-entered-in-error', text: 'his-tag-text-white' },
  未知: { bg: 'his-outpatient-unknown', text: 'his-tag-text-white' },
  // 英文 code 映射
  draft: { bg: 'his-outpatient-planned', text: 'his-tag-text-white' },
  'in-progress': { bg: 'his-outpatient-in-progress', text: 'his-tag-text-white' },
  'on-hold': { bg: 'his-outpatient-on-hold', text: 'his-tag-text-white' },
  'on-discharged': { bg: 'his-outpatient-discharged', text: 'his-tag-text-white' },
  completed: { bg: 'his-outpatient-completed', text: 'his-tag-text-white' },
  cancelled: { bg: 'his-outpatient-cancelled', text: 'his-tag-text-white' },
  discontinued: { bg: 'his-outpatient-discontinued', text: 'his-tag-text-white' },
  'entered-in-error': { bg: 'his-outpatient-entered-in-error', text: 'his-tag-text-white' },
  unknown: { bg: 'his-outpatient-unknown', text: 'his-tag-text-white' },
};

// 兼容旧的状态映射配置（保留向后兼容）
const STATUS_MAP: Record<string, { bg: string; text: string }> = {
  待入科: { bg: 'his-status-pending-admission', text: 'his-tag-text-white' },
  已入院: { bg: 'his-status-admitted', text: 'his-tag-text-white' },
  待出院: { bg: 'his-status-pending-discharge', text: 'his-tag-text-white' },
  已出院: { bg: 'his-status-discharged', text: 'his-tag-text-white' },
  候诊中: { bg: 'his-status-waiting', text: 'his-tag-text-white' },
  就诊中: { bg: 'his-status-treating', text: 'his-tag-text-white' },
  已完成: { bg: 'his-status-completed', text: 'his-tag-text-white' },
  已取消: { bg: 'his-status-cancelled', text: 'his-tag-text-white' },
};

// 支付类型映射配置
const PAYMENT_TYPE_MAP: Record<string, { bg: string; text: string }> = {
  自费: { bg: 'his-payment-self-pay', text: 'his-tag-text-white' },
  医保: { bg: 'his-payment-medical-insurance', text: 'his-tag-text-white' },
  公费: { bg: 'his-payment-public-expense', text: 'his-tag-text-white' },
  新农合: { bg: 'his-payment-rural-cooperative', text: 'his-tag-text-white' },
};

// 优先级映射配置
const PRIORITY_MAP: Record<string, { bg: string; text: string }> = {
  普通: { bg: 'his-priority-general', text: 'his-tag-text-gray' },
  优先: { bg: 'his-priority-priority', text: 'his-tag-text-white' },
  紧急: { bg: 'his-priority-urgent', text: 'his-tag-text-white' },
  危重: { bg: 'his-priority-critical', text: 'his-tag-text-white' },
};

// 性别映射配置
const GENDER_MAP: Record<string, { bg: string; text: string }> = {
  男性: { bg: 'his-gender-male', text: 'his-tag-text-white' },
  男: { bg: 'his-gender-male', text: 'his-tag-text-white' },
  女性: { bg: 'his-gender-female', text: 'his-tag-text-white' },
  女: { bg: 'his-gender-female', text: 'his-tag-text-white' },
};

// 登记状态映射配置
const REGISTRATION_MAP: Record<string, { bg: string; text: string }> = {
  无档登记: { bg: 'his-registration-unregistered', text: 'his-tag-text-gray' },
  已登记: { bg: 'his-registration-registered', text: 'his-tag-text-white' },
};

/**
 * 获取住院状态标签的颜色类名
 * @param status 状态文本（支持中文名称或英文 code）
 * @returns 包含背景色和文字颜色的类名对象
 */
export function getInpatientStatusTagColors(status: string) {
  return (
    INPATIENT_STATUS_MAP[status] || {
      bg: 'his-inpatient-already-settled',
      text: 'his-tag-text-white',
    }
  );
}

/**
 * 获取门诊状态标签的颜色类名
 * @param status 状态文本（支持中文名称或英文 code）
 * @returns 包含背景色和文字颜色的类名对象
 */
export function getOutpatientStatusTagColors(status: string) {
  return (
    OUTPATIENT_STATUS_MAP[status] || {
      bg: 'his-outpatient-unknown',
      text: 'his-tag-text-white',
    }
  );
}

/**
 * 获取状态标签的颜色类名（兼容旧版本，自动识别住院/门诊）
 * @param status 状态文本
 * @returns 包含背景色和文字颜色的类名对象
 */
export function getStatusTagColors(status: string) {
  // 优先尝试住院状态
  if (INPATIENT_STATUS_MAP[status]) {
    return INPATIENT_STATUS_MAP[status];
  }
  // 尝试门诊状态
  if (OUTPATIENT_STATUS_MAP[status]) {
    return OUTPATIENT_STATUS_MAP[status];
  }
  // 兼容旧的状态映射
  return STATUS_MAP[status] || { bg: 'his-status-discharged', text: 'his-tag-text-white' };
}

/**
 * 获取支付类型标签的颜色类名
 * @param paymentType 支付类型文本
 * @returns 包含背景色和文字颜色的类名对象
 */
export function getPaymentTypeTagColors(paymentType: string) {
  return (
    PAYMENT_TYPE_MAP[paymentType] || { bg: 'his-payment-self-pay', text: 'his-tag-text-white' }
  );
}

/**
 * 获取优先级标签的颜色类名
 * @param priority 优先级文本
 * @returns 包含背景色和文字颜色的类名对象
 */
export function getPriorityTagColors(priority: string) {
  return PRIORITY_MAP[priority] || { bg: 'his-priority-general', text: 'his-tag-text-gray' };
}

/**
 * 获取性别标签的颜色类名
 * @param gender 性别文本
 * @returns 包含背景色和文字颜色的类名对象
 */
export function getGenderTagColors(gender: string) {
  return GENDER_MAP[gender] || { bg: 'his-priority-general', text: 'his-tag-text-gray' };
}

/**
 * 获取登记状态标签的颜色类名
 * @param registrationStatus 登记状态文本
 * @returns 包含背景色和文字颜色的类名对象
 */
export function getRegistrationTagColors(registrationStatus: string) {
  return (
    REGISTRATION_MAP[registrationStatus] || {
      bg: 'his-registration-unregistered',
      text: 'his-tag-text-gray',
    }
  );
}

/**
 * 获取住院状态标签的内联样式（用于动态样式）
 * @param status 状态文本（支持中文名称或英文 code）
 * @returns 包含背景色和文字颜色的样式对象
 */
export function getInpatientStatusTagStyles(status: string) {
  const colorMap: Record<string, { bgColor: string; textColor: string }> = {
    // 中文名称
    待登记: { bgColor: '#E6A23C', textColor: '#FFFFFF' },
    待入科: { bgColor: '#409EFF', textColor: '#FFFFFF' },
    待出院: { bgColor: '#E6A23C', textColor: '#FFFFFF' },
    待出院结算: { bgColor: '#FF9800', textColor: '#FFFFFF' },
    已入院: { bgColor: '#67C23A', textColor: '#FFFFFF' },
    已入科: { bgColor: '#67C23A', textColor: '#FFFFFF' },
    在院: { bgColor: '#67C23A', textColor: '#FFFFFF' },
    待转科: { bgColor: '#9C27B0', textColor: '#FFFFFF' },
    已结算出院: { bgColor: '#909399', textColor: '#FFFFFF' },
    取消入科: { bgColor: '#F56C6C', textColor: '#FFFFFF' },
    // 英文 code
    'to-be-registered': { bgColor: '#E6A23C', textColor: '#FFFFFF' },
    registered: { bgColor: '#409EFF', textColor: '#FFFFFF' },
    'awaiting-discharge': { bgColor: '#E6A23C', textColor: '#FFFFFF' },
    'discharged-from-hospital': { bgColor: '#FF9800', textColor: '#FFFFFF' },
    'admitted-to-the-hospital': { bgColor: '#67C23A', textColor: '#FFFFFF' },
    ADMITTED: { bgColor: '#67C23A', textColor: '#FFFFFF' },
    IN_PROGRESS: { bgColor: '#67C23A', textColor: '#FFFFFF' },
    'pending-transfer': { bgColor: '#9C27B0', textColor: '#FFFFFF' },
    'already-settled': { bgColor: '#909399', textColor: '#FFFFFF' },
    'cancel-registration': { bgColor: '#F56C6C', textColor: '#FFFFFF' },
  };
  return colorMap[status] || { bgColor: '#909399', textColor: '#FFFFFF' };
}

/**
 * 获取门诊状态标签的内联样式（用于动态样式）
 * @param status 状态文本（支持中文名称或英文 code）
 * @returns 包含背景色和文字颜色的样式对象
 */
export function getOutpatientStatusTagStyles(status: string) {
  const colorMap: Record<string, { bgColor: string; textColor: string }> = {
    // 中文名称
    待诊: { bgColor: '#E6A23C', textColor: '#FFFFFF' },
    在诊: { bgColor: '#67C23A', textColor: '#FFFFFF' },
    暂离: { bgColor: '#FF9800', textColor: '#FFFFFF' },
    诊毕: { bgColor: '#909399', textColor: '#FFFFFF' },
    完成: { bgColor: '#4CAF50', textColor: '#FFFFFF' },
    退号: { bgColor: '#F56C6C', textColor: '#FFFFFF' },
    已中断: { bgColor: '#9E9E9E', textColor: '#FFFFFF' },
    错误: { bgColor: '#F44336', textColor: '#FFFFFF' },
    未知: { bgColor: '#BDBDBD', textColor: '#FFFFFF' },
    // 英文 code
    draft: { bgColor: '#E6A23C', textColor: '#FFFFFF' },
    'in-progress': { bgColor: '#67C23A', textColor: '#FFFFFF' },
    'on-hold': { bgColor: '#FF9800', textColor: '#FFFFFF' },
    'on-discharged': { bgColor: '#909399', textColor: '#FFFFFF' },
    completed: { bgColor: '#4CAF50', textColor: '#FFFFFF' },
    cancelled: { bgColor: '#F56C6C', textColor: '#FFFFFF' },
    discontinued: { bgColor: '#9E9E9E', textColor: '#FFFFFF' },
    'entered-in-error': { bgColor: '#F44336', textColor: '#FFFFFF' },
    unknown: { bgColor: '#BDBDBD', textColor: '#FFFFFF' },
  };
  return colorMap[status] || { bgColor: '#BDBDBD', textColor: '#FFFFFF' };
}

/**
 * 获取状态标签的内联样式（兼容旧版本，自动识别住院/门诊）
 * @param status 状态文本
 * @returns 包含背景色和文字颜色的样式对象
 */
export function getStatusTagStyles(status: string) {
  // 优先尝试住院状态
  const inpatientStyle = getInpatientStatusTagStyles(status);
  if (
    inpatientStyle.bgColor !== '#909399' ||
    status.includes('待') ||
    status.includes('已') ||
    status.includes('取消')
  ) {
    // 如果是住院状态的关键字，返回住院样式
    const inpatientMap: Record<string, { bgColor: string; textColor: string }> = {
      待登记: { bgColor: '#E6A23C', textColor: '#FFFFFF' },
      待入科: { bgColor: '#409EFF', textColor: '#FFFFFF' },
      待出院: { bgColor: '#E6A23C', textColor: '#FFFFFF' },
      待出院结算: { bgColor: '#FF9800', textColor: '#FFFFFF' },
      已入院: { bgColor: '#67C23A', textColor: '#FFFFFF' },
      已入科: { bgColor: '#67C23A', textColor: '#FFFFFF' },
      在院: { bgColor: '#67C23A', textColor: '#FFFFFF' },
      待转科: { bgColor: '#9C27B0', textColor: '#FFFFFF' },
      已结算出院: { bgColor: '#909399', textColor: '#FFFFFF' },
      取消入科: { bgColor: '#F56C6C', textColor: '#FFFFFF' },
    };
    if (inpatientMap[status]) {
      return inpatientMap[status];
    }
  }

  // 尝试门诊状态
  const outpatientStyle = getOutpatientStatusTagStyles(status);
  if (outpatientStyle.bgColor !== '#BDBDBD') {
    return outpatientStyle;
  }

  // 兼容旧的状态映射
  const colorMap: Record<string, { bgColor: string; textColor: string }> = {
    待入科: { bgColor: '#409EFF', textColor: '#FFFFFF' },
    已入院: { bgColor: '#67C23A', textColor: '#FFFFFF' },
    待出院: { bgColor: '#E6A23C', textColor: '#FFFFFF' },
    已出院: { bgColor: '#909399', textColor: '#FFFFFF' },
    候诊中: { bgColor: '#E6A23C', textColor: '#FFFFFF' },
    就诊中: { bgColor: '#67C23A', textColor: '#FFFFFF' },
    已完成: { bgColor: '#909399', textColor: '#FFFFFF' },
    已取消: { bgColor: '#F56C6C', textColor: '#FFFFFF' },
  };
  return colorMap[status] || { bgColor: '#909399', textColor: '#FFFFFF' };
}

/**
 * 获取支付类型标签的内联样式
 * @param paymentType 支付类型文本
 * @returns 包含背景色和文字颜色的样式对象
 */
export function getPaymentTypeTagStyles(paymentType: string) {
  const colorMap: Record<string, { bgColor: string; textColor: string }> = {
    自费: { bgColor: '#66B3FF', textColor: '#FFFFFF' },
    医保: { bgColor: '#B3E5D1', textColor: '#FFFFFF' },
    公费: { bgColor: '#C5B3FF', textColor: '#FFFFFF' },
    新农合: { bgColor: '#B3E0E6', textColor: '#FFFFFF' },
  };
  return colorMap[paymentType] || { bgColor: '#B3D8FF', textColor: '#FFFFFF' };
}

/**
 * 获取优先级标签的内联样式
 * @param priority 优先级文本
 * @returns 包含背景色和文字颜色的样式对象
 */
export function getPriorityTagStyles(priority: string) {
  const colorMap: Record<string, { bgColor: string; textColor: string }> = {
    普通: { bgColor: '#E0E0E0', textColor: '#666666' },
    优先: { bgColor: '#FFB84D', textColor: '#FFFFFF' },
    紧急: { bgColor: '#F56C6C', textColor: '#FFFFFF' },
    危重: { bgColor: '#DC3545', textColor: '#FFFFFF' },
  };
  return colorMap[priority] || { bgColor: '#E0E0E0', textColor: '#666666' };
}

/**
 * 获取性别标签的内联样式
 * @param gender 性别文本
 * @returns 包含背景色和文字颜色的样式对象
 */
export function getGenderTagStyles(gender: string) {
  const colorMap: Record<string, { bgColor: string; textColor: string }> = {
    男性: { bgColor: '#66B3FF', textColor: '#FFFFFF' },
    男: { bgColor: '#66B3FF', textColor: '#FFFFFF' },
    女性: { bgColor: '#FFB3D8', textColor: '#FFFFFF' },
    女: { bgColor: '#FFB3D8', textColor: '#FFFFFF' },
  };
  return colorMap[gender] || { bgColor: '#E0E0E0', textColor: '#666666' };
}
