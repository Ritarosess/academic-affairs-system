import request from './request'

/** 全部学期 */
export function fetchSemesters() {
  return request.get('/admin/semesters')
}

/** 新增学期 */
export function createSemester(data) {
  return request.post('/admin/semesters', data)
}

/** 编辑学期 */
export function updateSemester(id, data) {
  return request.put(`/admin/semesters/${id}`, data)
}

/** 删除学期 */
export function deleteSemester(id) {
  return request.delete(`/admin/semesters/${id}`)
}

/** 设为当前学期 */
export function setCurrentSemester(id) {
  return request.put(`/admin/semesters/${id}/current`)
}
