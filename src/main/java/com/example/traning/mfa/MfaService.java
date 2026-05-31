package com.example.traning.mfa;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MfaService {

    private static final int BACKUP_CODE_COUNT = 10;
    private static final String ISSUER = "TrainingApp";
    private static final String BACKUP_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final MfaSettingDao mfaSettingDao;
    private final MfaBackupCodeDao mfaBackupCodeDao;
    private final PasswordEncoder passwordEncoder;

    public MfaService(MfaSettingDao mfaSettingDao, MfaBackupCodeDao mfaBackupCodeDao,
            PasswordEncoder passwordEncoder) {
        this.mfaSettingDao = mfaSettingDao;
        this.mfaBackupCodeDao = mfaBackupCodeDao;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<UserMfaSetting> getSetting(Long userId) {
        return mfaSettingDao.selectByUserId(userId);
    }

    public boolean isEnabled(Long userId) {
        return mfaSettingDao.selectByUserId(userId)
                .map(UserMfaSetting::getIsEnabled)
                .orElse(false);
    }

    @Transactional
    public MfaSetupDto generateSetup(Long userId, String email) throws QrGenerationException {
        String secret = new DefaultSecretGenerator().generate();

        Optional<UserMfaSetting> existing = mfaSettingDao.selectByUserId(userId);
        if (existing.isPresent()) {
            UserMfaSetting setting = existing.get();
            setting.setSecretKey(secret);
            setting.setIsEnabled(false);
            setting.setUpdatedAt(LocalDateTime.now());
            mfaSettingDao.update(setting);
        } else {
            UserMfaSetting setting = new UserMfaSetting();
            setting.setUserId(userId);
            setting.setSecretKey(secret);
            mfaSettingDao.insert(setting);
        }

        QrData data = new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        ZxingPngQrGenerator generator = new ZxingPngQrGenerator();
        byte[] imageData = generator.generate(data);
        String mimeType = generator.getImageMimeType();
        String qrDataUri = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageData);

        return new MfaSetupDto(secret, qrDataUri);
    }

    public boolean verifyOtp(String secret, String code) {
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(
                new DefaultCodeGenerator(), new SystemTimeProvider());
        return verifier.isValidCode(secret, code);
    }

    @Transactional
    public List<String> enableMfa(Long userId, String otp) {
        UserMfaSetting setting = mfaSettingDao.selectByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("MFA設定が見つかりません。セットアップからやり直してください。"));

        if (!verifyOtp(setting.getSecretKey(), otp)) {
            throw new IllegalArgumentException("OTPコードが正しくありません。もう一度お試しください。");
        }

        setting.setIsEnabled(true);
        setting.setUpdatedAt(LocalDateTime.now());
        mfaSettingDao.update(setting);

        return generateAndSaveBackupCodes(userId);
    }

    @Transactional
    public List<String> regenerateBackupCodes(Long userId) {
        return generateAndSaveBackupCodes(userId);
    }

    @Transactional
    public boolean verifyBackupCode(Long userId, String code) {
        if (code == null || code.isBlank()) return false;
        String normalized = code.trim().toUpperCase();
        List<MfaBackupCode> codes = mfaBackupCodeDao.selectByUserId(userId);
        for (MfaBackupCode backup : codes) {
            if (!backup.getIsUsed() && passwordEncoder.matches(normalized, backup.getCodeHash())) {
                backup.setIsUsed(true);
                backup.setUsedAt(LocalDateTime.now());
                mfaBackupCodeDao.update(backup);
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void disableMfa(Long userId) {
        mfaSettingDao.deleteByUserId(userId);
        mfaBackupCodeDao.deleteByUserId(userId);
    }

    private List<String> generateAndSaveBackupCodes(Long userId) {
        mfaBackupCodeDao.deleteByUserId(userId);

        SecureRandom random = new SecureRandom();
        List<String> plainCodes = new ArrayList<>();

        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            StringBuilder sb = new StringBuilder(8);
            for (int j = 0; j < 8; j++) {
                sb.append(BACKUP_CHARS.charAt(random.nextInt(BACKUP_CHARS.length())));
            }
            String plain = sb.toString();
            plainCodes.add(plain);

            MfaBackupCode backupCode = new MfaBackupCode();
            backupCode.setUserId(userId);
            backupCode.setCodeHash(passwordEncoder.encode(plain));
            mfaBackupCodeDao.insert(backupCode);
        }

        return plainCodes;
    }
}
