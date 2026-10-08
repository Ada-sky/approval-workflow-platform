package com.ada.approval.api.service;

import com.ada.approval.api.dto.*;
import com.ada.approval.api.dto.ApiDtos.*;
import com.ada.approval.api.error.ApiException;
import com.ada.approval.entity.*;
import com.ada.approval.repository.*;
import com.ada.approval.service.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Coordinates administration DTOs, reference lookups and existing business services. Employee
 * creation resolves the existing Active status; profile editing preserves status rather than
 * deactivating employees.
 */
@Service
public class AdminApiService {
    @Autowired private EmployeeRepository employees;
    @Autowired private DeptRepository departments;
    @Autowired private RoleRepository roles;
    @Autowired private MenuRepository menus;
    @Autowired private TitleCategoryRepository titles;
    @Autowired private EmployeeStatusRepository statuses;
    @Autowired private HolidayTypeRepository leaveTypes;
    @Autowired private HolidayApplyRepository leaveRequests;
    @Autowired private AccountRoleRepository assignments;
    @Autowired private AccountRepository accounts;
    @Autowired private IEmployeeService employeeService;
    @Autowired private IDeptService departmentService;
    @Autowired private IRoleService roleService;
    @Autowired private IMenuService menuService;
    @Autowired private ITitleCategoryService titleService;
    @Autowired private IEmployeeStatusService statusService;
    @Autowired private IAccountRoleService assignmentService;
    @Autowired private IPermissionService permissionService;

    // Batch reference lookups per page avoid an entity graph or per-row queries.
    private <T> Set<Integer> ids(List<T> rows, java.util.function.Function<T, Integer> field) {
        return rows.stream()
                .map(field)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toSet());
    }

    private <T> Map<Integer, String> names(
            Iterable<T> rows,
            java.util.function.Function<T, Integer> id,
            java.util.function.Function<T, String> name) {
        Map<Integer, String> result = new HashMap<>();
        rows.forEach(row -> result.put(id.apply(row), name.apply(row)));
        return result;
    }

    private List<EmployeeResponse> employeeDisplays(List<Employee> rows) {
        Map<Integer, String> deps =
                names(
                        departments.findAllById(ids(rows, Employee::getDeptId)),
                        Dept::getId,
                        Dept::getDeptName);
        Map<Integer, String> jobs =
                names(
                        titles.findAllById(ids(rows, Employee::getTitleCategoryId)),
                        TitleCategory::getId,
                        t -> ApiMapper.displayJobTitle(t.getTitleName()));
        Map<Integer, String> states =
                names(
                        statuses.findAllById(ids(rows, Employee::getEmployStatusId)),
                        EmployeeStatus::getId,
                        EmployeeStatus::getName);
        return rows.stream()
                .map(
                        e -> {
                            EmployeeResponse dto = ApiMapper.employee(e);
                            dto.setDepartmentName(deps.get(e.getDeptId()));
                            dto.setJobTitleName(jobs.get(e.getTitleCategoryId()));
                            dto.setEmployeeStatusName(states.get(e.getEmployStatusId()));
                            return dto;
                        })
                .collect(Collectors.toList());
    }

    private List<DepartmentResponse> departmentDisplays(List<Dept> rows) {
        Map<Integer, String> managers =
                names(
                        employees.findAllById(ids(rows, Dept::getManagerId)),
                        Employee::getId,
                        Employee::getEmpName);
        Map<Integer, String> parents =
                names(
                        departments.findAllById(ids(rows, Dept::getParentId)),
                        Dept::getId,
                        Dept::getDeptName);
        return rows.stream()
                .map(
                        d -> {
                            DepartmentResponse dto = ApiMapper.department(d);
                            dto.setManagerName(managers.get(d.getManagerId()));
                            dto.setParentDepartmentName(parents.get(d.getParentId()));
                            return dto;
                        })
                .collect(Collectors.toList());
    }

    private String name(String name) {
        return name == null ? "" : name;
    }

    private Employee employee(Integer id) {
        return ApiPaging.required(employees.findByIdAndStatus(id, 1));
    }

    private Dept department(Integer id) {
        Dept d = departments.findById(id).orElse(null);
        return ApiPaging.required(d != null && Objects.equals(d.getStatus(), 1) ? d : null);
    }

    private Role role(Integer id) {
        Role r = roles.findById(id).orElse(null);
        return ApiPaging.required(r != null && Objects.equals(r.getStatus(), 1) ? r : null);
    }

    private Menu menu(Integer id) {
        Menu m = menus.findById(id).orElse(null);
        return ApiPaging.required(m != null && Objects.equals(m.getIsValid(), 1) ? m : null);
    }

    private TitleCategory title(Integer id) {
        TitleCategory t = titles.findById(id).orElse(null);
        return ApiPaging.required(t != null && Objects.equals(t.getStatus(), 1) ? t : null);
    }

    private EmployeeStatus status(Integer id) {
        EmployeeStatus s = statuses.findById(id).orElse(null);
        return ApiPaging.required(s != null && Objects.equals(s.getStatus(), 1) ? s : null);
    }

    private void employeeReferences(EmployeeRequest dto) {
        if (dto.getDepartmentId() != null) department(dto.getDepartmentId());
        if (dto.getJobTitleId() != null) title(dto.getJobTitleId());
    }

    public PageResponse<EmployeeResponse> employees(int page, int size, String name) {
        org.springframework.data.domain.Page<Employee> result =
                employees.findByStatusAndEmpNameContainingIgnoreCase(
                        1, name(name), ApiPaging.request(page, size));
        return new PageResponse<>(
                employeeDisplays(result.getContent()),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    public EmployeeFormOptions employeeFormOptions() {
        EmployeeFormOptions dto = new EmployeeFormOptions();
        dto.setDepartments(
                departments
                        .findByStatusAndDeptNameContainingIgnoreCase(
                                1, "", org.springframework.data.domain.Pageable.unpaged())
                        .getContent()
                        .stream()
                        .map(d -> ApiMapper.reference(d.getId(), d.getDeptName()))
                        .collect(Collectors.toList()));
        dto.setJobTitles(
                titles.findActiveCategories().stream()
                        .map(
                                t ->
                                        ApiMapper.reference(
                                                t.getId(),
                                                ApiMapper.displayJobTitle(t.getTitleName())))
                        .collect(Collectors.toList()));
        return dto;
    }

    public PageResponse<ReferenceResponse> managerOptions(int page, int size, String name) {
        return ApiPaging.map(
                employees.findByStatusAndEmpNameContainingIgnoreCase(
                        1, name(name), ApiPaging.request(page, size)),
                e -> ApiMapper.reference(e.getId(), e.getEmpName()));
    }

    public EmployeeResponse getEmployee(Integer id) {
        return employeeDisplays(Collections.singletonList(employee(id))).get(0);
    }

    /**
     * Creates the employee and linked account with the existing Active reference status. Returns
     * the generated temporary password only in this creation response; missing Active data fails
     * safely.
     */
    @Transactional
    public EmployeeCreatedResponse createEmployee(CreateEmployeeRequest dto) {
        employeeReferences(dto);
        List<EmployeeStatus> active = statuses.findAllByNameIgnoreCaseAndStatus("Active", 1);
        if (active.size() != 1 || active.get(0).getId() == null)
            throw new ApiException(
                    500, "Active employee status is unavailable. Please contact an administrator.");
        Employee e = ApiMapper.employee(dto);
        e.setEmployStatusId(active.get(0).getId());
        String temporaryPassword = com.ada.approval.config.security.TemporaryPassword.generate();
        employeeService.saveEmployee(e, temporaryPassword);
        EmployeeCreatedResponse response = new EmployeeCreatedResponse();
        org.springframework.beans.BeanUtils.copyProperties(
                employeeDisplays(Collections.singletonList(e)).get(0), response);
        response.setTemporaryPassword(temporaryPassword);
        return response;
    }

    /**
     * Edits profile data while retaining the persisted employment status. Ordinary profile editing
     * is not an employee-deactivation operation.
     */
    @Transactional
    public EmployeeResponse updateEmployee(Integer id, EmployeeRequest dto) {
        Employee existing = employee(id);
        employeeReferences(dto);
        Employee e = ApiMapper.employee(dto);
        e.setId(id);
        e.setEmployStatusId(existing.getEmployStatusId());
        employeeService.updateEmployee(e);
        return getEmployee(id);
    }

    @Transactional
    public void deleteEmployee(Integer id) {
        employee(id);
        employeeService.deleteEmployee(id);
    }

    public PageResponse<DepartmentResponse> departments(int page, int size, String name) {
        org.springframework.data.domain.Page<Dept> result =
                departments.findByStatusAndDeptNameContainingIgnoreCase(
                        1, name(name), ApiPaging.request(page, size));
        return new PageResponse<>(
                departmentDisplays(result.getContent()),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    public DepartmentResponse getDepartment(Integer id) {
        return departmentDisplays(Collections.singletonList(department(id))).get(0);
    }

    private void departmentReferences(DepartmentRequest dto) {
        if (dto.getParentId() != null && dto.getParentId() > 0) department(dto.getParentId());
        if (dto.getManagerEmployeeId() != null) employee(dto.getManagerEmployeeId());
    }

    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest dto) {
        departmentReferences(dto);
        Dept d = ApiMapper.department(dto);
        departmentService.saveDept(d);
        return departmentDisplays(Collections.singletonList(d)).get(0);
    }

    @Transactional
    public DepartmentResponse updateDepartment(Integer id, DepartmentRequest dto) {
        department(id);
        if (id.equals(dto.getParentId()))
            throw new ApiException(409, "A department cannot be its own parent");
        departmentReferences(dto);
        Dept d = ApiMapper.department(dto);
        d.setId(id);
        departmentService.upateDept(d);
        return getDepartment(id);
    }

    @Transactional
    public void deleteDepartment(Integer id) {
        department(id);
        departmentService.deleteDept(id);
    }

    public PageResponse<ReferenceResponse> roles(int page, int size, String name) {
        return ApiPaging.map(
                roles.findByStatusAndRoleNameContainingIgnoreCase(
                        1, name(name), ApiPaging.request(page, size)),
                r -> ApiMapper.reference(r.getId(), r.getRoleName()));
    }

    public ReferenceResponse getRole(Integer id) {
        Role r = role(id);
        return ApiMapper.reference(r.getId(), r.getRoleName());
    }

    @Transactional
    public ReferenceResponse createRole(NameRequest dto) {
        Role r = new Role();
        r.setRoleName(dto.getName());
        r.setStatus(1);
        roleService.saveRole(r);
        return ApiMapper.reference(r.getId(), r.getRoleName());
    }

    @Transactional
    public ReferenceResponse updateRole(Integer id, NameRequest dto) {
        role(id);
        Role r = new Role();
        r.setId(id);
        r.setRoleName(dto.getName());
        roleService.updateRole(r);
        return getRole(id);
    }

    @Transactional
    public void deleteRole(Integer id) {
        Role r = role(id);
        r.setStatus(0);
        roleService.updateById(r);
    }

    public PageResponse<AssignmentResponse> assignments(Integer roleId, int page, int size) {
        role(roleId);
        org.springframework.data.domain.Page<AccountRole> result =
                assignments.findByRoleId(roleId, ApiPaging.request(page, size));
        Set<Integer> ids = ids(result.getContent(), AccountRole::getAccountId);
        Map<Integer, Object[]> names = new HashMap<>();
        if (!ids.isEmpty())
            accounts.findAccountDisplayRows(ids).forEach(row -> names.put((Integer) row[0], row));
        return ApiPaging.map(
                result,
                a -> {
                    AssignmentResponse dto = ApiMapper.assignment(a);
                    Object[] row = names.get(a.getAccountId());
                    if (row != null) {
                        dto.setAccountEmail((String) row[2]);
                        dto.setAccountDisplayName(
                                row[1] == null ? (String) row[2] : (String) row[1]);
                    }
                    return dto;
                });
    }

    @Transactional
    public void assign(Integer roleId, Integer employeeId) {
        role(roleId);
        employee(employeeId);
        Account a = ApiPaging.required(accounts.findByEmpId(employeeId));
        if (!a.isEnabled()) throw new ApiException(409, "Employee account is disabled");
        if (assignments.findByAccountIdAndRoleId(a.getId(), roleId) != null)
            throw new ApiException(409, "Role is already assigned");
        assignmentService.saveAccountRole(roleId, employeeId);
    }

    @Transactional
    public void unassign(Integer roleId, Integer accountId) {
        role(roleId);
        if (assignments.findByAccountIdAndRoleId(accountId, roleId) == null)
            throw new ApiException(404, "Role assignment not found");
        assignmentService.cancelRoleToUser(roleId, accountId);
    }

    public List<PermissionResponse> grants(Integer roleId) {
        role(roleId);
        return menus.findAllById(permissionService.queryRoleHasAllMenuIdsByRoleId(roleId)).stream()
                .filter(m -> Objects.equals(m.getIsValid(), 1))
                .map(ApiMapper::permission)
                .collect(Collectors.toList());
    }

    @Transactional
    public void grants(Integer roleId, GrantRequest dto) {
        role(roleId);
        dto.getMenuIds().forEach(this::menu);
        roleService.addGrant(dto.getMenuIds().stream().distinct().toArray(Integer[]::new), roleId);
    }

    public PageResponse<PermissionResponse> permissions(int page, int size) {
        return ApiPaging.map(
                menus.findByIsValid(1, ApiPaging.request(page, size)), ApiMapper::permission);
    }

    public PermissionResponse getMenu(Integer id) {
        return ApiMapper.permission(menu(id));
    }

    @Transactional
    public PermissionResponse createMenu(MenuRequest dto) {
        if (dto.getParentId() != null && dto.getParentId() > 0) menu(dto.getParentId());
        Menu m = ApiMapper.menu(dto);
        menuService.saveMenu(m);
        return ApiMapper.permission(m);
    }

    @Transactional
    public PermissionResponse updateMenu(Integer id, MenuRequest dto) {
        menu(id);
        if (id.equals(dto.getParentId()))
            throw new ApiException(409, "A menu cannot be its own parent");
        Menu m = ApiMapper.menu(dto);
        m.setId(id);
        menuService.updateMenu(m);
        return getMenu(id);
    }

    @Transactional
    public void deleteMenu(Integer id) {
        menu(id);
        menuService.deleteMenu(id);
    }

    public PageResponse<ReferenceResponse> leaveTypes(int page, int size) {
        return ApiPaging.map(
                leaveTypes.findAll(ApiPaging.request(page, size)),
                t -> ApiMapper.reference(t.getId(), t.getHolidayType()));
    }

    public PageResponse<ReferenceResponse> searchLeaveTypes(int page, int size, String name) {
        return ApiPaging.map(
                leaveTypes.findByHolidayTypeContainingIgnoreCase(
                        name(name).strip(), ApiPaging.request(page, size)),
                t -> ApiMapper.reference(t.getId(), t.getHolidayType()));
    }

    public ReferenceResponse getLeaveType(Integer id) {
        HolidayType type = ApiPaging.required(leaveTypes.findById(id).orElse(null));
        return ApiMapper.reference(type.getId(), type.getHolidayType());
    }

    @Transactional
    public ReferenceResponse createLeaveType(LeaveTypeRequest dto) {
        if (leaveTypes.existsByHolidayTypeIgnoreCase(dto.getName()))
            throw new ApiException(409, "A leave type with this name already exists.");
        HolidayType type = new HolidayType();
        type.setHolidayType(dto.getName());
        type = leaveTypes.save(type);
        return ApiMapper.reference(type.getId(), type.getHolidayType());
    }

    @Transactional
    public ReferenceResponse updateLeaveType(Integer id, LeaveTypeRequest dto) {
        HolidayType type = ApiPaging.required(leaveTypes.findById(id).orElse(null));
        if (leaveTypes.existsByHolidayTypeIgnoreCaseAndIdNot(dto.getName(), id))
            throw new ApiException(409, "A leave type with this name already exists.");
        type.setHolidayType(dto.getName());
        leaveTypes.save(type);
        return ApiMapper.reference(type.getId(), type.getHolidayType());
    }

    @Transactional
    public void deleteLeaveType(Integer id) {
        HolidayType type = ApiPaging.required(leaveTypes.findById(id).orElse(null));
        if (leaveRequests.existsByHolidayType(id))
            throw new ApiException(
                    409,
                    "This leave type is already used by existing requests and cannot be deleted.");
        leaveTypes.delete(type);
    }

    public PageResponse<ReferenceResponse> statuses(int page, int size, String name) {
        return ApiPaging.map(
                statuses.findByStatusAndNameContainingIgnoreCase(
                        1, name(name), ApiPaging.request(page, size)),
                s -> ApiMapper.reference(s.getId(), s.getName()));
    }

    public ReferenceResponse getStatus(Integer id) {
        EmployeeStatus s = status(id);
        return ApiMapper.reference(s.getId(), s.getName());
    }

    @Transactional
    public ReferenceResponse createStatus(NameRequest dto) {
        EmployeeStatus s = new EmployeeStatus();
        s.setName(dto.getName());
        statusService.saveEmployeeStatus(s);
        return ApiMapper.reference(s.getId(), s.getName());
    }

    @Transactional
    public ReferenceResponse updateStatus(Integer id, NameRequest dto) {
        status(id);
        EmployeeStatus s = new EmployeeStatus();
        s.setId(id);
        s.setName(dto.getName());
        statusService.updateEmployeeStatus(s);
        return getStatus(id);
    }

    @Transactional
    public void deleteStatus(Integer id) {
        status(id);
        statusService.deleteEmployeeStatus(new Integer[] {id});
    }

    public PageResponse<JobTitleResponse> titles(int page, int size, String name) {
        return ApiPaging.map(
                titles.findByStatusAndTitleNameContainingIgnoreCase(
                        1, name(name), ApiPaging.request(page, size)),
                ApiMapper::title);
    }

    public JobTitleResponse getTitle(Integer id) {
        return ApiMapper.title(title(id));
    }

    @Transactional
    public JobTitleResponse createTitle(JobTitleRequest dto) {
        if (dto.getParentId() != null && dto.getParentId() > 0) title(dto.getParentId());
        TitleCategory t = ApiMapper.title(dto);
        titleService.savetTitleCategory(t);
        return ApiMapper.title(t);
    }

    @Transactional
    public JobTitleResponse updateTitle(Integer id, JobTitleRequest dto) {
        title(id);
        if (id.equals(dto.getParentId()))
            throw new ApiException(409, "A job title cannot be its own parent");
        TitleCategory t = ApiMapper.title(dto);
        t.setId(id);
        titleService.updateTitleCategory(t);
        return getTitle(id);
    }

    @Transactional
    public void deleteTitle(Integer id) {
        title(id);
        titleService.deleteTitleCategory(id);
    }
}
