-- Explicit local development fixture only; never a Flyway migration.
INSERT INTO `t_employee_status` (`id`, `name`, `status`, `create_time`, `update_time`) VALUES
  (1, 'Active', 1, @seed_time, @seed_time);

INSERT INTO `t_holiday_type` (`id`, `holiday_type`, `create_time`, `update_time`) VALUES
  (1, 'Annual Leave', @seed_time, @seed_time),
  (2, 'Sick Leave', @seed_time, @seed_time),
  (3, 'Personal Leave', @seed_time, @seed_time);

INSERT INTO `t_title_category` (`id`, `title_num`, `title_name`, `parent_id`, `level`, `status`, `create_time`, `update_time`) VALUES
  (1, 'T001', 'Employee', 0, 0, 1, @seed_time, @seed_time),
  (2, 'T002', 'Department Manager', 0, 0, 1, @seed_time, @seed_time),
  (3, 'T003', 'General Manager', 0, 0, 1, @seed_time, @seed_time),
  (4, 'T004', 'HR', 0, 0, 1, @seed_time, @seed_time),
  (5, 'T005', 'Administrator', 0, 0, 1, @seed_time, @seed_time);

INSERT INTO `t_dept` (`id`, `dept_num`, `dept_name`, `parent_id`, `level`, `manager_id`, `status`, `create_time`, `update_time`) VALUES
  (1, 'D001', 'Operations', 0, 0, NULL, 1, @seed_time, @seed_time),
  (2, 'D002', 'Administration', 0, 0, NULL, 1, @seed_time, @seed_time);

INSERT INTO `t_employee` (`id`, `emp_num`, `emp_name`, `email`, `mobile`, `dept_id`, `title_category_id`, `employ_status_id`, `status`, `create_time`, `update_time`) VALUES
  (1, '000001', 'Employee A', 'employee.a@example.test', '15500000001', 1, 1, 1, 1, @seed_time, @seed_time),
  (2, '000002', 'Employee B', 'employee.b@example.test', '15500000002', 1, 1, 1, 1, @seed_time, @seed_time),
  (3, '000003', 'Department Manager', 'manager@example.test', '15500000003', 1, 2, 1, 1, @seed_time, @seed_time),
  (4, '000004', 'General Manager', 'general.manager@example.test', '15500000004', 2, 3, 1, 1, @seed_time, @seed_time),
  (5, '000005', 'HR Approver', 'hr@example.test', '15500000005', 2, 4, 1, 1, @seed_time, @seed_time),
  (6, '000006', 'Administrator', 'admin@example.test', '15500000006', 2, 5, 1, 1, @seed_time, @seed_time);

INSERT INTO `t_account` (`id`, `user_name`, `password`, `emp_id`, `status`, `create_time`, `update_time`) VALUES
  (11, 'employee.a@example.test', @employee_a_hash, 1, 1, @seed_time, @seed_time),
  (12, 'employee.b@example.test', @employee_b_hash, 2, 1, @seed_time, @seed_time),
  (13, 'manager@example.test', @manager_hash, 3, 1, @seed_time, @seed_time),
  (14, 'general.manager@example.test', @general_manager_hash, 4, 1, @seed_time, @seed_time),
  (15, 'hr@example.test', @hr_hash, 5, 1, @seed_time, @seed_time),
  (16, 'admin@example.test', @administrator_hash, 6, 1, @seed_time, @seed_time);

INSERT INTO `t_role` (`id`, `role_name`, `status`, `create_time`, `update_time`) VALUES
  (1, 'Employee', 1, @seed_time, @seed_time),
  (2, 'Department Manager', 1, @seed_time, @seed_time),
  (3, 'General Manager', 1, @seed_time, @seed_time),
  (4, 'HR', 1, @seed_time, @seed_time),
  (5, 'Administrator', 1, @seed_time, @seed_time);

INSERT INTO `t_account_role` (`id`, `account_id`, `role_id`) VALUES
  (1, 11, 1),
  (2, 12, 1),
  (3, 13, 2),
  (4, 14, 3),
  (5, 15, 4),
  (6, 16, 5);

INSERT INTO `t_menu` (`id`, `menu_name`, `url`, `parent_id`, `grade`, `opt_value`, `orders`, `is_valid`, `create_time`, `update_time`) VALUES
  (201, 'Departments', '/dept/index', 0, 0, '10011', 1, 1, @seed_time, @seed_time),
  (202, 'Job Titles', '/titleCategory/index', 0, 0, '10012', 2, 1, @seed_time, @seed_time),
  (203, 'Employee Statuses', '/employeeStatus/index', 0, 0, '10013', 3, 1, @seed_time, @seed_time),
  (204, 'Employees', '/employee/index', 0, 0, '10021', 4, 1, @seed_time, @seed_time),
  (205, 'Create Employee', '/employee/addEmployeePage', 0, 0, '100211', 5, 1, @seed_time, @seed_time),
  (206, 'Update Employee', '/employee/updateEmployeePage', 0, 0, '100212', 6, 1, @seed_time, @seed_time),
  (207, 'Delete Employee', '/employee/delete', 0, 0, '100213', 7, 1, @seed_time, @seed_time),
  (208, 'Read Employees', '/employee/list', 0, 0, '100214', 8, 1, @seed_time, @seed_time),
  (209, 'Roles', '/role/index', 0, 0, '10022', 9, 1, @seed_time, @seed_time),
  (210, 'Menus', '/menu/index', 0, 0, '10023', 10, 1, @seed_time, @seed_time),
  (300, 'Pending Approvals', '/workBench/toTaskListPage', 0, 0, 'integration.workbench', 20, 1, @seed_time, @seed_time);

INSERT INTO `t_permission` (`id`, `role_id`, `menu_id`, `acl_value`, `create_time`, `update_time`) VALUES
  (1, 5, 201, '10011', @seed_time, @seed_time),
  (2, 5, 202, '10012', @seed_time, @seed_time),
  (3, 5, 203, '10013', @seed_time, @seed_time),
  (4, 5, 204, '10021', @seed_time, @seed_time),
  (5, 5, 205, '100211', @seed_time, @seed_time),
  (6, 5, 206, '100212', @seed_time, @seed_time),
  (7, 5, 207, '100213', @seed_time, @seed_time),
  (8, 5, 208, '100214', @seed_time, @seed_time),
  (9, 5, 209, '10022', @seed_time, @seed_time),
  (10, 5, 210, '10023', @seed_time, @seed_time),
  (11, 2, 300, 'integration.workbench', @seed_time, @seed_time),
  (12, 3, 300, 'integration.workbench', @seed_time, @seed_time),
  (13, 4, 300, 'integration.workbench', @seed_time, @seed_time);
