package com.example.traning.mobile.exception;

public class OAuthOnlyException extends RuntimeException {
  public OAuthOnlyException() {
    super("OAUTH_ONLY_ACCOUNT");
  }
}
