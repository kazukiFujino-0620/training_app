package com.example.traning.user;

import java.time.LocalDateTime;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

@Entity
@Table(name = "account_restore_tokens")
public class AccountRestoreToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @Column(name = "user_id")
    public Integer userId;

    public String token;

    @Column(name = "expiry_date")
    public LocalDateTime expiryDate;

    public void setUserId(Integer userId) { this.userId = userId; }
    public void setToken(String token) { this.token = token; }
    public void setExpiryDate(LocalDateTime expiryDate) { this.expiryDate = expiryDate; }
}
