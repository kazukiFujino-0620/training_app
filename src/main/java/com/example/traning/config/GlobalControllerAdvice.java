package com.example.traning.config;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
    public void addUserToModel(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            User user = userDao.selectByEmail(userDetails.getUsername())
                    .orElse(null);
            if (user != null) {
                model.addAttribute("loginUser", user);
            }
        }
    }
}
