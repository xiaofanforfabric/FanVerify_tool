package com.xiaofan.fanVerify.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class FanVerifyAPI {
    
    private static final Logger log = LoggerFactory.getLogger(FanVerifyAPI.class);
    private static final String BASE_URL = "https://api.fanverify.cn";
    private static final Gson gson = new Gson();
    
    /**
     * OTP申请接口
     * @param accessToken 开发者令牌
     * @return OTP字符串 或 null（失败时）
     */
    public static String requestOTP(String accessToken) {
        try {
            String urlStr = String.format("%s/openapi/otp?accesstoken=%s",
                BASE_URL,
                URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
            );
            
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();
                
                JsonObject json = gson.fromJson(response.toString(), JsonObject.class);
                boolean success = json.get("success").getAsBoolean();
                
                if (success) {
                    return json.getAsJsonObject("data").get("otp").getAsString();
                } else {
                    log.warn("OTP申请失败: success=false");
                    return null;
                }
            } else {
                log.error("OTP申请失败: HTTP {}", responseCode);
                return null;
            }
            
        } catch (Exception e) {
            log.error("OTP申请异常", e);
            return null;
        }
    }
    
    /**
     * OTP二维码生成接口
     * @param accessToken 开发者令牌
     * @param otp OTP字符串
     * @return 二维码图片 或 null（失败时）
     */
    public static BufferedImage generateQRCode(String accessToken, String otp) {
        try {
            String urlStr = String.format("%s/openapi/genqrcode?accesstoken=%s&otp=%s",
                BASE_URL,
                URLEncoder.encode(accessToken, StandardCharsets.UTF_8),
                URLEncoder.encode(otp, StandardCharsets.UTF_8)
            );
            
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                InputStream in = conn.getInputStream();
                BufferedImage image = ImageIO.read(in);
                in.close();
                return image;
            } else {
                log.error("二维码生成失败: HTTP {}", responseCode);
                return null;
            }
            
        } catch (Exception e) {
            log.error("二维码生成异常", e);
            return null;
        }
    }
    
    /**
     * OTP轮询接口
     * @param accessToken 开发者令牌
     * @param otp OTP字符串
     * @return OTPPollResponse
     */
    public static OTPPollResponse pollOTP(String accessToken, String otp) {
        try {
            String urlStr = String.format("%s/openapi/seeotp?accesstoken=%s&otp=%s",
                BASE_URL,
                URLEncoder.encode(accessToken, StandardCharsets.UTF_8),
                URLEncoder.encode(otp, StandardCharsets.UTF_8)
            );
            
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();
                
                JsonObject json = gson.fromJson(response.toString(), JsonObject.class);
                String status = json.get("status").getAsString();
                
                if ("ok".equals(status)) {
                    // 验证成功
                    JsonObject userData = json.getAsJsonArray("data").get(0).getAsJsonObject();
                    return new OTPPollResponse(
                        OTPStatus.SUCCESS,
                        userData.get("uid").getAsInt(),
                        userData.get("level").getAsString(),
                        userData.get("reg_time").getAsString(),
                        userData.get("tag").getAsString()
                    );
                } else if ("wait".equals(status)) {
                    // 等待验证
                    return new OTPPollResponse(OTPStatus.WAITING, 0, null, null, null);
                } else if ("rate_limit".equals(status)) {
                    // 请求过快
                    return new OTPPollResponse(OTPStatus.RATE_LIMIT, 0, null, null, null);
                } else {
                    return new OTPPollResponse(OTPStatus.ERROR, 0, null, null, null);
                }
                
            } else if (responseCode == 429) {
                // 请求过多
                return new OTPPollResponse(OTPStatus.RATE_LIMIT, 0, null, null, null);
            } else {
                log.error("OTP轮询失败: HTTP {}", responseCode);
                return new OTPPollResponse(OTPStatus.ERROR, 0, null, null, null);
            }
            
        } catch (Exception e) {
            log.error("OTP轮询异常", e);
            return new OTPPollResponse(OTPStatus.ERROR, 0, null, null, null);
        }
    }
    
    /**
     * 用户验证接口
     * @param accessToken 开发者令牌
     * @param uid 用户UID
     * @param passCode 动态验证码
     * @return UserVerifyResponse 或 null（失败时）
     */
    public static UserVerifyResponse verifyUser(String accessToken, String uid, String passCode) {
        try {
            String urlStr = String.format("%s/openapi/user_verify?accesstoken=%s&uid=%s&pass_code=%s",
                BASE_URL,
                URLEncoder.encode(accessToken, StandardCharsets.UTF_8),
                URLEncoder.encode(uid, StandardCharsets.UTF_8),
                URLEncoder.encode(passCode, StandardCharsets.UTF_8)
            );
            
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();
                
                JsonObject json = gson.fromJson(response.toString(), JsonObject.class);
                String status = json.get("status").getAsString();
                
                if ("ok".equals(status)) {
                    JsonObject userData = json.getAsJsonArray("data").get(0).getAsJsonObject();
                    return new UserVerifyResponse(
                        true,
                        userData.get("uid").getAsInt(),
                        userData.get("level").getAsString(),
                        userData.get("reg_time").getAsString(),
                        userData.get("tag").getAsString(),
                        null
                    );
                } else {
                    log.warn("验证失败: status={}", status);
                    return new UserVerifyResponse(false, 0, null, null, null, "验证失败");
                }
                
            } else if (responseCode == 401) {
                log.error("API 鉴权失败: 401 Unauthorized");
                return new UserVerifyResponse(false, 0, null, null, null, "鉴权失败");
            } else {
                log.error("API 请求失败: HTTP {}", responseCode);
                return new UserVerifyResponse(false, 0, null, null, null, "请求失败");
            }
            
        } catch (Exception e) {
            log.error("API 调用异常", e);
            return new UserVerifyResponse(false, 0, null, null, null, "网络异常");
        }
    }
    
    /**
     * OTP状态
     */
    public enum OTPStatus {
        SUCCESS,    // 验证成功
        WAITING,    // 等待验证
        RATE_LIMIT, // 请求过快
        ERROR       // 错误
    }
    
    /**
     * OTP轮询响应
     */
    public static class OTPPollResponse {
        public final OTPStatus status;
        public final int uid;
        public final String level;
        public final String regTime;
        public final String tag;
        
        public OTPPollResponse(OTPStatus status, int uid, String level, String regTime, String tag) {
            this.status = status;
            this.uid = uid;
            this.level = level;
            this.regTime = regTime;
            this.tag = tag;
        }
    }
    
    /**
     * 用户验证响应
     */
    public static class UserVerifyResponse {
        public final boolean success;
        public final int uid;
        public final String level;
        public final String regTime;
        public final String tag;
        public final String error;
        
        public UserVerifyResponse(boolean success, int uid, String level, String regTime, String tag, String error) {
            this.success = success;
            this.uid = uid;
            this.level = level;
            this.regTime = regTime;
            this.tag = tag;
            this.error = error;
        }
    }
}
