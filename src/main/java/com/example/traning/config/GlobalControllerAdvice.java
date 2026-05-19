package com.example.traning.config;

import java.security.Principal;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.example.traning.dao.UserDao;
import com.example.traning.user.User;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final UserDao userDao;

    public GlobalControllerAdvice(UserDao userDao) {
        this.userDao = userDao;
    }

    @ModelAttribute
    public void addUserToModel(Model model, Principal principal) {
        if (principal != null) {
            User user = userDao.selectByEmail(principal.getName())
                    .orElse(null);
            if (user != null) {
                model.addAttribute("loginUser", user);
            }
        }
    }
}
