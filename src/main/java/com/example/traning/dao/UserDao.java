package com.example.traning.dao;

import java.util.Optional;

import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;
import org.seasar.doma.jdbc.Result;

import com.example.traning.entity.User;

@Dao
@ConfigAutowireable
public interface UserDao {
	@Insert
	Result<User> insert(User user);

	@Select
	Optional<User> selectByEmail(String email);
}