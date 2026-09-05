package com.lexpro.lexprobackend.auth.service;

import com.lexpro.lexprobackend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CaptchaService {
    private static final String CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> entries = new HashMap<>();

    public synchronized Challenge create() {
        Instant now = Instant.now();
        entries.values().removeIf(entry -> !entry.expiresAt().isAfter(now));
        if (entries.size() >= 10000) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many challenges",
                    "CAPTCHA_CAPACITY_REACHED", "请稍后重试");
        }
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 4; i++) code.append(CHARS.charAt(random.nextInt(CHARS.length())));
        String id = UUID.randomUUID().toString();
        entries.put(id, new Entry(code.toString(), now.plusSeconds(300)));
        StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"110\" height=\"40\" viewBox=\"0 0 110 40\"><rect width=\"110\" height=\"40\" fill=\"#eef2f7\" rx=\"6\"/>");
        for (int i = 0; i < 4; i++) {
            int x = 12 + i * 24, y = 26 + random.nextInt(7), rotation = random.nextInt(37) - 18;
            String color = "#%02x%02x%02x".formatted(40 + random.nextInt(121), 40 + random.nextInt(121), 40 + random.nextInt(121));
            svg.append("<text x=\"%d\" y=\"%d\" font-size=\"22\" font-weight=\"bold\" fill=\"%s\" transform=\"rotate(%d %d %d)\" font-family=\"Arial\">%s</text>".formatted(x, y, color, rotation, x, y, code.charAt(i)));
        }
        for (int i = 0; i < 5; i++) svg.append("<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"#9aa7b8\" stroke-width=\"1\"/>".formatted(random.nextInt(111), random.nextInt(41), random.nextInt(111), random.nextInt(41)));
        return new Challenge(id, svg.append("</svg>").toString());
    }

    public synchronized void verify(String id, String answer) {
        Entry entry = id == null ? null : entries.remove(id);
        if (entry == null || !entry.expiresAt().isAfter(Instant.now()) || answer == null
                || !entry.code().equalsIgnoreCase(answer.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid captcha", "CAPTCHA_INVALID", "验证码错误或已过期");
        }
    }
    private record Entry(String code, Instant expiresAt) {}
    public record Challenge(String captchaId, String svg) {}
}
