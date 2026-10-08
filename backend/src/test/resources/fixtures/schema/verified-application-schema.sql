-- Isolated bootstrap, ONLY the 12 application tables. No ACT_* tables, DROP, or IF NOT EXISTS.
-- Original DDL unavailable: lengths/nullability below follow current service/DTO requirements.
-- INT IDs/scalar references; VARCHAR(255) unless bounded request fields require otherwise.
-- DATETIME(6) preserves LocalDateTime/Date timestamp fields; employee dates use DATE.
-- Optional fields nullable; status/parent defaults are NOT invented. Seed/services supply them.
-- No speculative FK/unique constraints. Only PKs and useful non-unique lookup indexes.
-- Run as batch without --force, on the separate instance with --database=hpoa_integration.
SET NAMES utf8mb4;
SET @integration_guard_sql = IF(DATABASE() = 'hpoa_integration' AND @@port = 3307,
  'DO 0', 'INTEGRATION_TARGET_MISMATCH');
PREPARE integration_guard FROM @integration_guard_sql;
EXECUTE integration_guard;
DEALLOCATE PREPARE integration_guard;

CREATE TABLE `t_account` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `user_name` VARCHAR(255) NOT NULL,
  `password` VARCHAR(100) NOT NULL,
  `create_time` DATETIME(6) NULL,
  `update_time` DATETIME(6) NULL,
  `emp_id` INT NOT NULL,
  `status` INT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_account_1` (`user_name`),
  KEY `idx_account_2` (`emp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `t_account_role` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `account_id` INT NOT NULL,
  `role_id` INT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_account_role_1` (`account_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `t_dept` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `dept_num` VARCHAR(255) NOT NULL,
  `dept_name` VARCHAR(255) NOT NULL,
  `parent_id` INT NULL,
  `level` INT NULL,
  `manager_id` INT NULL,
  `create_time` DATETIME(6) NULL,
  `update_time` DATETIME(6) NULL,
  `status` INT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_dept_1` (`manager_id`),
  KEY `idx_dept_2` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `t_employee` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `emp_num` VARCHAR(255) NOT NULL,
  `emp_name` VARCHAR(255) NOT NULL,
  `gender` VARCHAR(255) NULL,
  `birthday` DATE NULL,
  `location` VARCHAR(255) NULL,
  `on_board_date` DATE NULL,
  `mobile` VARCHAR(255) NOT NULL,
  `qq` VARCHAR(255) NULL,
  `email` VARCHAR(255) NOT NULL,
  `weixin` VARCHAR(255) NULL,
  `dept_id` INT NULL,
  `title_category_id` INT NULL,
  `title_id` INT NULL,
  `employ_status_id` INT NULL,
  `graduate_school` VARCHAR(255) NULL,
  `education` VARCHAR(255) NULL,
  `formal_status` VARCHAR(255) NULL,
  `status` INT NOT NULL,
  `create_time` DATETIME(6) NULL,
  `update_time` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_employee_1` (`email`),
  KEY `idx_employee_2` (`dept_id`),
  KEY `idx_employee_3` (`title_category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `t_employee_status` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(255) NOT NULL,
  `create_time` DATETIME(6) NULL,
  `update_time` DATETIME(6) NULL,
  `status` INT NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `t_holiday_apply` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `account_id` INT NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `user_name` VARCHAR(255) NULL,
  `reason` VARCHAR(1000) NOT NULL,
  `days` INT NOT NULL,
  `holiday_type` INT NOT NULL,
  `submit_time` DATETIME(6) NOT NULL,
  `remark` VARCHAR(1000) NULL,
  `status` INT NOT NULL,
  `approval_status` INT NULL,
  `process_instance_id` VARCHAR(255) NULL,
  `start_time` DATETIME(6) NULL,
  `end_time` DATETIME(6) NULL,
  `create_time` DATETIME(6) NULL,
  `update_time` DATETIME(6) NULL,
  `is_valid` INT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_holiday_apply_1` (`account_id`, `is_valid`, `id`),
  KEY `idx_holiday_apply_2` (`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `t_holiday_approval` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `process_instance_id` VARCHAR(255) NOT NULL,
  `task_id` VARCHAR(255) NOT NULL,
  `user_id` INT NOT NULL,
  `result` INT NOT NULL,
  `remark` VARCHAR(1000) NULL,
  `user_name` VARCHAR(255) NOT NULL,
  `task_def_key` VARCHAR(255) NOT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_time` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_holiday_approval_1` (`process_instance_id`, `create_time`, `id`),
  KEY `idx_holiday_approval_2` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `t_holiday_type` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `holiday_type` VARCHAR(255) NOT NULL,
  `create_time` DATETIME(6) NULL,
  `update_time` DATETIME(6) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `t_menu` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `menu_name` VARCHAR(255) NOT NULL,
  `menu_style` VARCHAR(255) NULL,
  `url` VARCHAR(255) NULL,
  `parent_id` INT NULL,
  `parent_opt_value` VARCHAR(255) NULL,
  `grade` INT NULL,
  `opt_value` VARCHAR(255) NOT NULL,
  `orders` INT NULL,
  `is_valid` INT NOT NULL,
  `create_time` DATETIME(6) NULL,
  `update_time` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_menu_1` (`parent_id`),
  KEY `idx_menu_2` (`opt_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `t_permission` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `role_id` INT NOT NULL,
  `menu_id` INT NOT NULL,
  `acl_value` VARCHAR(255) NOT NULL,
  `create_time` DATETIME(6) NULL,
  `update_time` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_permission_1` (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `t_role` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `role_name` VARCHAR(255) NOT NULL,
  `create_time` DATETIME(6) NULL,
  `update_time` DATETIME(6) NULL,
  `status` INT NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `t_title_category` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `title_num` VARCHAR(255) NOT NULL,
  `title_name` VARCHAR(255) NOT NULL,
  `parent_id` INT NULL,
  `create_time` DATETIME(6) NULL,
  `update_time` DATETIME(6) NULL,
  `status` INT NOT NULL,
  `level` INT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_title_category_1` (`title_name`),
  KEY `idx_title_category_2` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

