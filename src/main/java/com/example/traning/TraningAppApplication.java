package com.example.traning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TraningAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(TraningAppApplication.class, args);
	}

//	@Bean
//	public CommandLineRunner demo(UserDao userDao) {
//		return args -> {
//			System.out.println("--- DB検索テスト開始 ---");
//			userDao.selectByEmail("test@example.com").ifPresentOrElse(
//					user -> System.out.println("ユーザー発見: " + user.getUserName()),
//					() -> System.out.println("ユーザーが見つかりません..."));
//			System.out.println("--- DB検索テスト終了 ---");
//		};
//	}
}