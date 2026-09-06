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

import java.util.Arrays;
import java.util.Collections;

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
            return LawSearchResponse.success(Collections.emptyList(), aiResponse.getReply(), Arrays.asList(
                    new LawSearchResponse.OfficialSource("国家法律法规数据库", "核验现行法律、行政法规和部门规章", "https://flk.npc.gov.cn/"),
                    new LawSearchResponse.OfficialSource("中国政府网", "核验国务院政策文件与行政法规", "https://www.gov.cn/"),
                    new LawSearchResponse.OfficialSource("最高人民法院", "核验司法解释与审判指导信息", "https://www.court.gov.cn/")
            ));
        } else {
            return LawSearchResponse.error(aiResponse.getMessage());
        }
    }

    @GetMapping("/health")
    public ChatResponse health() {
        return deepSeekService.health();
    }
}
