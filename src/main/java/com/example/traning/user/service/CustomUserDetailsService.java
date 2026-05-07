package com.example.traning.user.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.traning.dao.UserDao;
import com.example.traning.user.User;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

	private final UserDao userDao;

	public CustomUserDetailsService(UserDao userDao) {
		this.userDao = userDao;
	}

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
		log.debug("認証開始: user={}", user.getEmail());
		String roleName = user.getRole().replace("ROLE_", "");

		return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
				.password(user.getPassword())
				.roles(roleName)
				.build();
	}
}
