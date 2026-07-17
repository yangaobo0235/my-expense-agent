CREATE TABLE my_expense_agent_applicant (
    applicant_id VARCHAR(80) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    college_code VARCHAR(80) NOT NULL,
    applicant_type VARCHAR(32) NOT NULL,
    region VARCHAR(32) NOT NULL,
    currency VARCHAR(8) NOT NULL
);

CREATE TABLE my_expense_agent_reimbursement_account (
    applicant_id VARCHAR(80) NOT NULL
        REFERENCES my_expense_agent_applicant(applicant_id) ON DELETE CASCADE,
    account_type VARCHAR(80) NOT NULL,
    PRIMARY KEY (applicant_id, account_type)
);

CREATE TABLE my_expense_agent_project (
    project_code VARCHAR(80) PRIMARY KEY,
    project_name VARCHAR(200) NOT NULL,
    college_code VARCHAR(80) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE my_expense_agent_project_member (
    project_code VARCHAR(80) NOT NULL
        REFERENCES my_expense_agent_project(project_code) ON DELETE CASCADE,
    applicant_id VARCHAR(80) NOT NULL
        REFERENCES my_expense_agent_applicant(applicant_id) ON DELETE CASCADE,
    campus_role VARCHAR(32) NOT NULL,
    PRIMARY KEY (project_code, applicant_id)
);

CREATE INDEX idx_my_expense_agent_project_member_applicant
    ON my_expense_agent_project_member (applicant_id, project_code);

CREATE TABLE my_expense_agent_project_budget (
    project_code VARCHAR(80) PRIMARY KEY
        REFERENCES my_expense_agent_project(project_code) ON DELETE CASCADE,
    total_amount NUMERIC(18, 2) NOT NULL CHECK (total_amount >= 0),
    available_amount NUMERIC(18, 2) NOT NULL CHECK (available_amount >= 0),
    currency VARCHAR(8) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (available_amount <= total_amount)
);

CREATE TABLE my_expense_agent_budget_debit (
    debit_id UUID PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL UNIQUE,
    case_id UUID NOT NULL,
    project_code VARCHAR(80) NOT NULL
        REFERENCES my_expense_agent_project(project_code),
    applicant_id VARCHAR(80) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    remaining_available NUMERIC(18, 2) NOT NULL CHECK (remaining_available >= 0),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_my_expense_agent_budget_debit_project
    ON my_expense_agent_budget_debit (project_code, created_at DESC);

INSERT INTO my_expense_agent_applicant (
    applicant_id, name, college_code, applicant_type, region, currency
) VALUES
    ('student01', '李明', 'COMPUTER_SCIENCE', 'STUDENT', 'CN', 'CNY'),
    ('advisor01', '王老师', 'COMPUTER_SCIENCE', 'ADVISOR', 'CN', 'CNY'),
    ('collegeReviewer01', '学院经费审核员', 'COMPUTER_SCIENCE', 'COLLEGE_REVIEWER', 'CN', 'CNY'),
    ('finance01', '校级财务管理员', 'UNIVERSITY_FINANCE', 'FINANCE_ADMIN', 'CN', 'CNY'),
    ('auditor01', '经费审计员', 'UNIVERSITY_AUDIT', 'AUDITOR', 'CN', 'CNY')
ON CONFLICT (applicant_id) DO NOTHING;

INSERT INTO my_expense_agent_reimbursement_account (applicant_id, account_type) VALUES
    ('student01', 'PERSONAL_ADVANCE'),
    ('student01', 'CAMPUS_CARD'),
    ('advisor01', 'PROJECT_ACCOUNT'),
    ('collegeReviewer01', 'COLLEGE_FINANCE_ACCOUNT'),
    ('finance01', 'UNIVERSITY_FINANCE_ACCOUNT')
ON CONFLICT (applicant_id, account_type) DO NOTHING;

INSERT INTO my_expense_agent_project (
    project_code, project_name, college_code, status
) VALUES (
    'CS-SRTP', '计算机学院大学生科研训练项目', 'COMPUTER_SCIENCE', 'ACTIVE'
)
ON CONFLICT (project_code) DO NOTHING;

INSERT INTO my_expense_agent_project_member (project_code, applicant_id, campus_role) VALUES
    ('CS-SRTP', 'student01', 'STUDENT'),
    ('CS-SRTP', 'advisor01', 'ADVISOR')
ON CONFLICT (project_code, applicant_id) DO NOTHING;

INSERT INTO my_expense_agent_project_budget (
    project_code, total_amount, available_amount, currency
) VALUES (
    'CS-SRTP', 50000.00, 50000.00, 'CNY'
)
ON CONFLICT (project_code) DO NOTHING;

CREATE VIEW my_expense_agent_applicant_account AS
SELECT
    applicant.applicant_id,
    applicant.name,
    member.project_code,
    member.campus_role AS campus_level,
    applicant.region,
    budget.available_amount AS budget_balance,
    budget.currency
FROM my_expense_agent_project_member member
JOIN my_expense_agent_applicant applicant
  ON applicant.applicant_id = member.applicant_id
JOIN my_expense_agent_project_budget budget
  ON budget.project_code = member.project_code;
