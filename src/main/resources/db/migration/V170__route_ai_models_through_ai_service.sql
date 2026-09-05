update ai_model_policies
set provider_name = 'ai-service',
    updated_at = now()
where provider_name = 'openai-compatible';
