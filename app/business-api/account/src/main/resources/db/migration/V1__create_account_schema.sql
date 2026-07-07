CREATE TABLE expense_account_employee (
    employee_id VARCHAR(80) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    department_code VARCHAR(80) NOT NULL,
    employee_grade VARCHAR(32) NOT NULL,
    region VARCHAR(32) NOT NULL,
    available_balance NUMERIC(18, 2) NOT NULL CHECK (available_balance >= 0),
    currency VARCHAR(8) NOT NULL
);

CREATE TABLE expense_account_payment_method (
    employee_id VARCHAR(80) NOT NULL REFERENCES expense_account_employee(employee_id) ON DELETE CASCADE,
    payment_method VARCHAR(80) NOT NULL,
    PRIMARY KEY (employee_id, payment_method)
);

INSERT INTO expense_account_employee (
    employee_id, name, department_code, employee_grade, region, available_balance, currency
) VALUES
    ('employee01', '费用员工', 'IT', 'G6', 'CN', 5000.00, 'CNY'),
    ('reviewer01', '审核人员', 'FINANCE', 'G8', 'CN', 20000.00, 'CNY')
ON CONFLICT (employee_id) DO NOTHING;

INSERT INTO expense_account_payment_method (employee_id, payment_method) VALUES
    ('employee01', 'CORPORATE_CARD'),
    ('employee01', 'PERSONAL_ADVANCE'),
    ('reviewer01', 'CORPORATE_CARD')
ON CONFLICT (employee_id, payment_method) DO NOTHING;
