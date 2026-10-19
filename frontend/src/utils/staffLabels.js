// Staff enum label maps. Kept as explicit literals (not built from templates) so
// the i18n checker can see every key that is used.

export const DEPARTMENTS = [
  { value: 'PRODUCT_MANAGER', labelKey: 'staff.dept.PRODUCT_MANAGER' },
  { value: 'INVENTORY', labelKey: 'staff.dept.INVENTORY' },
  { value: 'ORDER_MANAGER', labelKey: 'staff.dept.ORDER_MANAGER' },
  { value: 'CUSTOMER_SUPPORT', labelKey: 'staff.dept.CUSTOMER_SUPPORT' },
  { value: 'DELIVERY', labelKey: 'staff.dept.DELIVERY' },
  { value: 'MARKETING', labelKey: 'staff.dept.MARKETING' },
];

export const STAFF_STATUSES = [
  { value: 'PENDING', labelKey: 'staff.status.PENDING' },
  { value: 'MORE_INFO', labelKey: 'staff.status.MORE_INFO' },
  { value: 'APPROVED', labelKey: 'staff.status.APPROVED' },
  { value: 'REJECTED', labelKey: 'staff.status.REJECTED' },
  { value: 'SUSPENDED', labelKey: 'staff.status.SUSPENDED' },
  { value: 'DEACTIVATED', labelKey: 'staff.status.DEACTIVATED' },
];

export const TASK_STATUSES = [
  { value: 'TODO', labelKey: 'staff.taskStatus.TODO' },
  { value: 'IN_PROGRESS', labelKey: 'staff.taskStatus.IN_PROGRESS' },
  { value: 'BLOCKED', labelKey: 'staff.taskStatus.BLOCKED' },
  { value: 'COMPLETED', labelKey: 'staff.taskStatus.COMPLETED' },
  { value: 'CANCELLED', labelKey: 'staff.taskStatus.CANCELLED' },
];

export const TASK_PRIORITIES = [
  { value: 'LOW', labelKey: 'staff.priority.LOW' },
  { value: 'MEDIUM', labelKey: 'staff.priority.MEDIUM' },
  { value: 'HIGH', labelKey: 'staff.priority.HIGH' },
  { value: 'URGENT', labelKey: 'staff.priority.URGENT' },
];

export const APPROVAL_TYPES = [
  { value: 'PRICE_CHANGE', labelKey: 'staff.approval.type.PRICE_CHANGE' },
  { value: 'DISCOUNT', labelKey: 'staff.approval.type.DISCOUNT' },
];

// Plain identifiers, rendered as-is (not translated).
export const ALL_PERMISSIONS = [
  'PRODUCT_VIEW',
  'PRODUCT_CREATE',
  'PRODUCT_EDIT',
  'PRICE_CHANGE_REQUEST',
  'INVENTORY_VIEW',
  'INVENTORY_UPDATE',
  'ORDER_VIEW',
  'ORDER_FULFILL',
  'SUPPORT_VIEW',
  'SUPPORT_REPLY',
  'DELIVERY_UPDATE',
  'TASK_VIEW',
  'TASK_MANAGE',
  'STAFF_MANAGE',
  'REFUND_APPROVE',
];

export function labelKeyFor(list, value) {
  return list.find((item) => item.value === value)?.labelKey || value || '';
}
