package com.example.traning.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.traning.dao.UserDao;
import com.example.traning.entity.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserDao userDao;

    /**
     * 全ユーザーを取得する
     */
    public List<User> findAll() {
        log.info("全ユーザー情報の取得を開始します。");
        return userDao.selectAll();
    }

    /**
     * ユーザー情報を更新する（今後、権限変更などで使用）
     */
    public boolean updateUserInfo(User user) {
        boolean result = userDao.update(user).getCount() > 0;

        return result;
    }

    public User getUserById(Integer id) {
        log.info("ユーザーID: {} の取得を開始します。", id);
        // Doma2のDao経由で取得
        User user = userDao.selectById(id);

        if (user == null) {
            throw new RuntimeException("ユーザーが見つかりません ID:" + id);
        }
        return user;
    }
}