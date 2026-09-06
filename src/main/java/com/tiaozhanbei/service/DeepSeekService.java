package com.tiaozhanbei.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.tiaozhanbei.config.DeepSeekConfig;
import com.tiaozhanbei.dto.ChatRequest;
import com.tiaozhanbei.dto.ChatResponse;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class DeepSeekService {
    private static final Logger logger = LoggerFactory.getLogger(DeepSeekService.class);

    private final DeepSeekConfig deepSeekConfig;
    private final OkHttpClient httpClient;

    @Autowired
    public DeepSeekService(DeepSeekConfig deepSeekConfig) {
        this.deepSeekConfig = deepSeekConfig;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public ChatResponse chat(ChatRequest request) {
        if (isBlank(deepSeekConfig.getKey())) {
            return ChatResponse.error("AI 服务尚未配置");
        }
        if (request == null || isBlank(request.getMessage())) {
            return ChatResponse.error("请输入咨询内容");
        }
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", deepSeekConfig.getModel());

            List<JSONObject> messages = new ArrayList<>();

            if (request.getHistory() != null && !request.getHistory().isEmpty()) {
                for (ChatRequest.ChatMessage historyMsg : request.getHistory()) {
                    JSONObject historyMessage = new JSONObject();
                    historyMessage.put("role", historyMsg.getRole());
                    historyMessage.put("content", historyMsg.getContent());
                    messages.add(historyMessage);
                }
            }

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", request.getMessage());
            messages.add(userMessage);

            requestBody.put("messages", messages);

            RequestBody body = RequestBody.create(
                    requestBody.toJSONString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request httpRequest = new Request.Builder()
                    .url(deepSeekConfig.getUrl())
                    .addHeader("Authorization", "Bearer " + deepSeekConfig.getKey())
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("DeepSeek API error: {}", response);
                    return ChatResponse.error("AI 服务调用失败: " + response.code());
                }

                String responseBody = response.body().string();
                JSONObject jsonResponse = JSON.parseObject(responseBody);

                if (jsonResponse.containsKey("error")) {
                    String errorMsg = jsonResponse.getJSONObject("error").getString("message");
                    logger.error("DeepSeek API error: {}", errorMsg);
                    return ChatResponse.error("AI 服务错误: " + errorMsg);
                }

                JSONArray choices = jsonResponse.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    JSONObject firstChoice = choices.getJSONObject(0);
                    JSONObject message = firstChoice.getJSONObject("message");
                    String reply = message.getString("content");
                    return ChatResponse.success(reply);
                }

                return ChatResponse.error("AI 返回数据格式错误");
            }
        } catch (IOException e) {
            logger.error("Network error calling DeepSeek API", e);
            return ChatResponse.error("网络错误: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error calling DeepSeek API", e);
            return ChatResponse.error("系统错误: " + e.getMessage());
        }
    }

    public ChatResponse searchLaws(String keyword, String category) {
        String searchPrompt = String.format(
                "你是法律检索助手。用户查询关键词为'%s'。%s仅说明应检索的法律领域、关键词和核验要点，不得编造或引用具体法条、条款号、发布日期或法律效力。请提醒用户以国家法律法规数据库等官方来源核验。",
                keyword,
                category != null && !category.isEmpty() ? "限定分类：" + category : ""
        );

        ChatRequest request = new ChatRequest();
        request.setMessage(searchPrompt);

        return chat(request);
    }

    public ChatResponse health() {
        return isBlank(deepSeekConfig.getKey())
                ? ChatResponse.error("AI 服务尚未配置")
                : ChatResponse.success("AI 服务已配置");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
