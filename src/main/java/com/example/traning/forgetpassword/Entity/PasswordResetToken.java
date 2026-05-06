package com.example.traning.forgetpassword.entity;

import java.math.BigInteger;
import java.time.LocalDateTime;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @Column(name = "user_id")
    public BigInteger userId;

    @Column(name = "token")
    public String token;

    @Column(name = "expiry_date")
    public LocalDateTime expiryDate;
}