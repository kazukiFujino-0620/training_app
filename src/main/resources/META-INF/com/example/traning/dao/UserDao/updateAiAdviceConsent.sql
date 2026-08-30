UPDATE users
SET ai_advice_consent = /* aiAdviceConsent */false,
    update_Datetime = CURRENT_TIMESTAMP
WHERE id = /* userId */0
