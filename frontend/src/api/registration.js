import request from './request'

/* ---------------- 公开：自助注册 ---------------- */

/** 提交注册申请（无需登录） */
export function submitRegister(data) {
  return request.post('/auth/register', data)
}

/** 注册页班级下拉框数据（无需登录） */
export function fetchRegisterClazzs() {
  return request.get('/auth/register/clazzs')
}

/* ---------------- 管理员：注册审核 ---------------- */

/** 分页查询注册申请，params: { status, page, size } */
export function fetchRegistrations(params) {
  return request.get('/admin/registrations', { params })
}

/** 审核通过，data: { remark } */
export function approveRegistration(id, data) {
  return request.put(`/admin/registrations/${id}/approve`, data)
}

/** 审核拒绝，data: { remark } */
export function rejectRegistration(id, data) {
  return request.put(`/admin/registrations/${id}/reject`, data)
}
