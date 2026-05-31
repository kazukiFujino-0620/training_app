package com.example.traning.training.service;

import java.time.LocalDate;
import java.time.Period;
import com.example.traning.user.User;
import org.springframework.stereotype.Component;

@Component
public class CalorieCalculator {

    private static final double MET = 5.0;

    public enum CalorieType { FULL, SIMPLE, UNSET }

    public static class CalorieEstimate {
        public final CalorieType type;
        public final Integer calories;
        public CalorieEstimate(CalorieType type, Integer calories) {
            this.type = type;
            this.calories = calories;
        }
    }

    public CalorieEstimate estimate(User user, int durationMinutes) {
        if (user == null || durationMinutes <= 0) {
            return new CalorieEstimate(CalorieType.UNSET, null);
        }
        Double weight = user.getWeightKg();
        if (weight == null) {
            return new CalorieEstimate(CalorieType.UNSET, null);
        }
        double hours = durationMinutes / 60.0;

        Double height = user.getHeightCm();
        String gender = user.getGender();
        LocalDate birthDate = user.getBirthDate();
        boolean canUseFull = height != null && birthDate != null
                && ("MALE".equals(gender) || "FEMALE".equals(gender));

        if (canUseFull) {
            int age = Period.between(birthDate, LocalDate.now()).getYears();
            double bmi = weight / Math.pow(height / 100.0, 2);
            double sexCoef = "MALE".equals(gender) ? 1.0 : 0.0;
            double bodyFatPct = 1.20 * bmi + 0.23 * age - 10.8 * sexCoef - 5.4;
            bodyFatPct = Math.max(3.0, Math.min(55.0, bodyFatPct));
            double leanMass = weight * (1.0 - bodyFatPct / 100.0);
            return new CalorieEstimate(CalorieType.FULL, (int) Math.round(MET * leanMass * hours));
        } else {
            return new CalorieEstimate(CalorieType.SIMPLE, (int) Math.round(MET * weight * hours));
        }
    }
}
