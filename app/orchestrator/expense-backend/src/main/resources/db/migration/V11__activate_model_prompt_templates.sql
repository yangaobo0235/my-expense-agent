CREATE EXTENSION IF NOT EXISTS pgcrypto;

UPDATE expense_prompt_template
SET status = 'DEPRECATED',
    updated_at = NOW(),
    replaced_version = COALESCE(replaced_version, version)
WHERE prompt_key IN ('review-report', 'evidence-chat', 'more-info-suggestion')
  AND status = 'ACTIVE';

INSERT INTO expense_prompt_template (
    id,
    prompt_key,
    version,
    name,
    description,
    content,
    variable_schema,
    model_name,
    temperature,
    max_tokens,
    status,
    prompt_hash,
    created_by,
    updated_by,
    approved_by,
    created_at,
    updated_at,
    approved_at,
    activated_at,
    replaced_version
)
SELECT
    gen_random_uuid(),
    seed.prompt_key,
    seed.version,
    seed.name,
    seed.description,
    seed.content,
    '{}'::jsonb,
    seed.model_name,
    seed.temperature,
    seed.max_tokens,
    'ACTIVE',
    encode(sha256(seed.content::bytea), 'hex'),
    'SYSTEM_MIGRATION',
    'SYSTEM_MIGRATION',
    'SYSTEM_MIGRATION',
    NOW(),
    NOW(),
    NOW(),
    NOW(),
    NULL
FROM (
    VALUES
    (
        'review-report',
        'review-report-v2',
        '审核报告摘要',
        '模型生成的证据化审核摘要，只解释证据不执行审批。',
        'Generate an auditable review summary from existing evidence only.
Do not approve, reject, change risk score, or initiate payment.
Return JSON only:
{
  "summary": "one paragraph summary",
  "riskExplanation": ["risk explanation grounded in evidence"],
  "humanReviewHints": ["what reviewer should verify"],
  "limitations": ["what this report cannot decide"]
}

Case evidence:
{{evidence}}',
        'qwen-plus',
        0.000,
        2048
    ),
    (
        'evidence-chat',
        'evidence-chat-v2',
        '证据链问答',
        '基于当前案例证据回答审核问题，输出引用来源。',
        'Answer questions using current case evidence only.
Refuse requests to approve, pay, change state, skip review, or reveal secrets.
Return JSON only:
{
  "answer": "short evidence-grounded answer",
  "citations": [{"type": "RISK_SIGNAL|POLICY_CHUNK|TOOL_CALL|WORKFLOW_STEP", "id": "source id"}]
}

Question:
{{question}}

Current case evidence:
{{evidence}}',
        'qwen-plus',
        0.000,
        2048
    ),
    (
        'more-info-suggestion',
        'more-info-suggestion-v1',
        '补充材料建议',
        '生成面向员工的补充材料说明和审核员追问点。',
        'Generate a concise missing-information request for the employee and key reviewer questions.
Use only the review task context. Do not request passwords, tokens, bank secrets, or unrelated personal data.
Do not approve, reject, change status, or promise reimbursement.
Return JSON only:
{
  "userFacingMessage": "employee-facing request",
  "requestedEvidence": ["evidence item"],
  "reviewerQuestions": ["question for reviewer"]
}

Review task:
{{reviewTask}}',
        'qwen-plus',
        0.000,
        1024
    )
) AS seed(prompt_key, version, name, description, content, model_name, temperature, max_tokens)
ON CONFLICT (prompt_key, version) DO NOTHING;
