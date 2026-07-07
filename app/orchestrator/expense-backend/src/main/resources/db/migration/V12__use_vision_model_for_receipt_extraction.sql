UPDATE expense_prompt_template
SET model_name = 'qwen-vl-plus',
    updated_at = NOW()
WHERE prompt_key = 'receipt-extraction'
  AND status = 'ACTIVE'
  AND model_name = 'qwen-plus';
