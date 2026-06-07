package com.example.traning.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

@Entity(immutable = true)
@Table(name = "users")
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  public Integer userId;

  public String email;

  @JsonIgnore
  @Column(name = "password")
  public String password;

  @Column(name = "user_Name")
  public String userName;

  public String role;
  public Boolean enabled;

  @JsonIgnore
  @Column(name = "google_Id")
  public String googleId;

  @JsonIgnore
  @Column(name = "line_Id")
  public String lineId;

  @Column(name = "create_Datetime")
  public LocalDateTime createDatetime;

  @Column(name = "update_Datetime")
  public LocalDateTime updatedDatetime;

  @Column(name = "deleted_at")
  public LocalDateTime deletedAt;

  @Column(name = "height_cm")
  public Double heightCm;

  @Column(name = "weight_kg")
  public Double weightKg;

  public String gender;

  @Column(name = "birth_date")
  public LocalDate birthDate;
}
