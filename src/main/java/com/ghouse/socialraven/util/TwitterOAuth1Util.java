package com.ghouse.socialraven.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TwitterOAuth1Util {

    public static String generateAuthorizationHeader(
            String apiKey,
            String apiSecret,
            String accessToken,
            String accessSecret,
            String method,
            String url,
            Map<String, String> params
    ) throws Exception {

        String oauthNonce = UUID.randomUUID().toString().replaceAll("-", "");
        String oauthTimestamp = String.valueOf(System.currentTimeMillis() / 1000);

        Map<String, String> oauthParams = new LinkedHashMap<>();
        oauthParams.put("oauth_consumer_key", apiKey);
        oauthParams.put("oauth_nonce", oauthNonce);
        oauthParams.put("oauth_signature_method", "HMAC-SHA1");
        oauthParams.put("oauth_timestamp", oauthTimestamp);
        oauthParams.put("oauth_token", accessToken);
        oauthParams.put("oauth_version", "1.0");

        // merge params
        Map<String, String> allParams = new TreeMap<>();
        allParams.putAll(oauthParams);
        allParams.putAll(params);

        // create signature base string
        StringBuilder base = new StringBuilder();
        for (Map.Entry<String, String> e : allParams.entrySet()) {
            base.append(encode(e.getKey())).append("=").append(encode(e.getValue())).append("&");
        }
        String baseString = method.toUpperCase() + "&" + encode(url) + "&" + encode(base.substring(0, base.length() - 1));

        // signing key
        String signingKey = encode(apiSecret) + "&" + encode(accessSecret);

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(signingKey.getBytes(), "HmacSHA1"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal(baseString.getBytes()));

        oauthParams.put("oauth_signature", signature);

        // build header
        StringBuilder header = new StringBuilder("OAuth ");
        for (Map.Entry<String, String> e : oauthParams.entrySet()) {
            header.append(encode(e.getKey())).append("=\"").append(encode(e.getValue())).append("\", ");
        }
        return header.substring(0, header.length() - 2);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
