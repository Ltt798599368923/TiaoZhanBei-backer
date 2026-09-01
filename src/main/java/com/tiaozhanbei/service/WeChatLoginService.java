package com.tiaozhanbei.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.tiaozhanbei.config.WeChatConfig;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

@Service
public class WeChatLoginService {
    private static final String CODE_TO_SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    private final WeChatConfig weChatConfig;
    private final OkHttpClient httpClient;

    public WeChatLoginService(WeChatConfig weChatConfig) {
        this.weChatConfig = weChatConfig;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public String resolveOpenId(String code, String nickname) {
        if (!weChatConfig.isLoginEnabled()) {
            return "dev_openid_" + sha256(normalizeNickname(nickname));
        }

        if (isBlank(weChatConfig.getAppId()) || isBlank(weChatConfig.getAppSecret())) {
            throw new IllegalStateException("WeChat login is enabled but credentials are not configured");
        }
        if (isBlank(code)) {
            throw new IllegalArgumentException("WeChat login code is required");
        }

        HttpUrl url = HttpUrl.parse(CODE_TO_SESSION_URL).newBuilder()
                .addQueryParameter("appid", weChatConfig.getAppId())
                .addQueryParameter("secret", weChatConfig.getAppSecret())
                .addQueryParameter("js_code", code)
                .addQueryParameter("grant_type", "authorization_code")
                .build();

        try (Response response = httpClient.newCall(new Request.Builder().url(url).get().build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("WeChat session exchange failed");
            }
            JSONObject body = JSON.parseObject(response.body().string());
            String openId = body.getString("openid");
            if (isBlank(openId)) {
                throw new IllegalArgumentException("WeChat login was rejected: " + body.getString("errmsg"));
            }
            return openId;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to contact WeChat login service", e);
        }
    }

    private String normalizeNickname(String nickname) {
        return isBlank(nickname) ? "anonymous" : nickname.trim().toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
