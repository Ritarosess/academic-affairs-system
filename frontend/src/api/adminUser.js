import request from './request'

/** 分页查询用户：role 与 keyword 可选 */
export function fetchUsers(params) {
  return request.get('/admin/users', { params })
}

/** 新增用户 */
export function createUser(data) {
  return request.post('/admin/users', data)
}

/** 编辑用户 */
export function updateUser(id, data) {
  return request.put(`/admin/users/${id}`, data)
}

/** 删除用户 */
export function deleteUser(id) {
  return request.delete(`/admin/users/${id}`)
}

/** 启用/停用 */
export function updateUserStatus(id, status) {
  return request.put(`/admin/users/${id}/status`, { status })
}

/** 重置密码，返回 { password } */
export function resetUserPassword(id) {
  return request.put(`/admin/users/${id}/reset-password`)
}
