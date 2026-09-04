package com.tiaozhanbei.controller;

import com.tiaozhanbei.dto.ChatRequest;
import com.tiaozhanbei.dto.ChatResponse;
import com.tiaozhanbei.dto.LawSearchRequest;
import com.tiaozhanbei.dto.LawSearchResponse;
import com.tiaozhanbei.service.DeepSeekService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AIController {
    private static final Logger logger = LoggerFactory.getLogger(AIController.class);

    private final DeepSeekService deepSeekService;

    @Autowired
    public AIController(DeepSeekService deepSeekService) {
        this.deepSeekService = deepSeekService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        logger.info("Received chat request: {}", request.getMessage());
        return deepSeekService.chat(request);
    }

    @PostMapping("/law/search")
    public LawSearchResponse searchLaws(@RequestBody LawSearchRequest request) {
        logger.info("Received law search request: keyword={}, category={}",
                request.getKeyword(), request.getCategory());

        if (request.getKeyword() == null || request.getKeyword().trim().isEmpty()) {
            return LawSearchResponse.error("关键词不能为空");
        }

        ChatResponse aiResponse = deepSeekService.searchLaws(
                request.getKeyword(),
                request.getCategory()
        );

        if (aiResponse.getCode() == 200) {
            return LawSearchResponse.success(null, aiResponse.getReply());
        } else {
            return LawSearchResponse.error(aiResponse.getMessage());
        }
    }

    @GetMapping("/health")
    public ChatResponse health() {
        return deepSeekService.health();
    }
}
