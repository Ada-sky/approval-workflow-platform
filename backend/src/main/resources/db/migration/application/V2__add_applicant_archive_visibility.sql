-- Applicant list visibility only; business and workflow history remain intact.
ALTER TABLE t_holiday_apply ADD COLUMN applicant_archived BOOLEAN NOT NULL DEFAULT FALSE;
