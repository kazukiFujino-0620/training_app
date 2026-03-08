package com.example.traning.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.traning.dao.UserDao;
import com.example.traning.entity.User;

@Service
public class CustomUserDetailsService implements UserDetailsService {

	@Autowired
	private UserDao userDao;

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

		// DBから検索
		User user = userDao.selectByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりませんでした: " + email));

		System.out.println("認証開始: user=" + user.getEmail() + ", pass=" + user.getPassword());

		boolean isMatch = new BCryptPasswordEncoder().matches("password", user.getPassword());
		System.out.println("パスワード一致テスト結果: " + isMatch);

		return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
				.password(user.getPassword()).roles("USER").build();
	}
}
