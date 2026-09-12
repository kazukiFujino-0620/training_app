UPDATE users
SET line_friend_added = /* lineFriendAdded */true,
    update_Datetime = CURRENT_TIMESTAMP
WHERE line_Id = /* lineId */'U1234567890'
