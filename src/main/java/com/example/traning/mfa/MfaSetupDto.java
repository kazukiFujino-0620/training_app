package com.example.traning.mfa;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MfaSetupDto {
    private String secret;
    private String qrDataUri;
}
