package com.example.traning.user;

import java.time.LocalDateTime;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

	@Column(name = "password")
	public String password;

	@Column(name = "user_Name")
	public String userName;

	public String role;
	public Boolean enabled;

	@Column(name = "google_Id")
	public String googleId;

	@Column(name = "create_Datetime")
	public LocalDateTime createDatetime;

	@Column(name = "update_Datetime")
	public LocalDateTime updatedDatetime;
}
