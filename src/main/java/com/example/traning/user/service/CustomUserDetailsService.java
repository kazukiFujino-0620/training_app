package com.example.traning.user.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.traning.dao.UserDao;
import com.example.traning.user.User;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

	@Autowired
	private UserDao userDao;

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		User user = userDao.selectByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりませんでした: " + email));

		if (Boolean.compare(user.getEnabled(), false) == 0) {
			String msg = "このメールアドレスは退会済みです: " + email +
					" 再度登録する場合は、管理者に連絡ください。";

			// 1. ログに出力
			log.error(msg);
			throw new UsernameNotFoundException(msg);
		}
		System.out.println("認証開始: user=" + user.getEmail() + ", pass=" + user.getPassword());
		String roleName = user.getRole().replace("ROLE_", "");

		boolean isMatch = new BCryptPasswordEncoder().matches("password", user.getPassword());
		System.out.println("パスワード一致テスト結果: " + isMatch);

		return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
				.password(user.getPassword())
				.roles(roleName)
				.build();
	}
}
