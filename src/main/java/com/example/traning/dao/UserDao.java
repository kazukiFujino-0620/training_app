package com.example.traning.dao;

import java.util.List;
import java.util.Optional;

import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
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

	@Select
	List<User> selectAll();

	@Update
	Result<User> update(User user);

	@Select
	User selectById(Integer userId);

	@Select
	List<User> selectByName(String userName);
}