UPDATE users
SET role_review_required = /* roleReviewRequired */false,
    update_Datetime = CURRENT_TIMESTAMP
WHERE id = /* userId */0
