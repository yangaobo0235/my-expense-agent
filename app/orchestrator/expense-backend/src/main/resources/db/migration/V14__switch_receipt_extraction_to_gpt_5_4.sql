UPDATE expense_prompt_template
SET model_name = 'gpt-5.4',
    updated_at = NOW()
WHERE prompt_key = 'receipt-extraction'
  AND status = 'ACTIVE'
  AND model_name <> 'gpt-5.4';
