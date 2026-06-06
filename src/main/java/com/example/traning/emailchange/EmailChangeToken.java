package com.example.traning.emailchange;

import java.time.LocalDateTime;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

@Entity
@Table(name = "email_change_tokens")
public class EmailChangeToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id")
    public Long userId;

    @Column(name = "new_email")
    public String newEmail;

    @Column(name = "token")
    public String token;

    @Column(name = "expiry_date")
    public LocalDateTime expiryDate;

    @Column(name = "created_at", insertable = false, updatable = false)
    public LocalDateTime createdAt;
}
