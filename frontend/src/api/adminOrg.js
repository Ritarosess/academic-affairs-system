import request from './request'

/* ---------------- 年级 ---------------- */
export function fetchGrades() {
  return request.get('/admin/org/grades')
}
export function createGrade(data) {
  return request.post('/admin/org/grades', data)
}
export function updateGrade(id, data) {
  return request.put(`/admin/org/grades/${id}`, data)
}
export function deleteGrade(id) {
  return request.delete(`/admin/org/grades/${id}`)
}

/* ---------------- 专业 ---------------- */
export function fetchMajors() {
  return request.get('/admin/org/majors')
}
export function createMajor(data) {
  return request.post('/admin/org/majors', data)
}
export function updateMajor(id, data) {
  return request.put(`/admin/org/majors/${id}`, data)
}
export function deleteMajor(id) {
  return request.delete(`/admin/org/majors/${id}`)
}

/* ---------------- 班级 ---------------- */
export function fetchClazzs() {
  return request.get('/admin/org/clazzs')
}
export function createClazz(data) {
  return request.post('/admin/org/clazzs', data)
}
export function updateClazz(id, data) {
  return request.put(`/admin/org/clazzs/${id}`, data)
}
export function deleteClazz(id) {
  return request.delete(`/admin/org/clazzs/${id}`)
}
