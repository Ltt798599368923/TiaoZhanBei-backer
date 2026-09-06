package com.tiaozhanbei.dto;

import java.util.List;

public class LawSearchResponse {
    private List<LawResult> results;
    private String aiAdvice;
    private List<OfficialSource> officialSources;
    private Integer code;
    private String message;

    public LawSearchResponse() {}

    public LawSearchResponse(Integer code, String message, List<LawResult> results, String aiAdvice) {
        this.code = code;
        this.message = message;
        this.results = results;
        this.aiAdvice = aiAdvice;
    }

    public static LawSearchResponse success(List<LawResult> results, String aiAdvice) {
        return new LawSearchResponse(200, "success", results, aiAdvice);
    }

    public static LawSearchResponse success(List<LawResult> results, String aiAdvice, List<OfficialSource> officialSources) {
        LawSearchResponse response = new LawSearchResponse(200, "success", results, aiAdvice);
        response.setOfficialSources(officialSources);
        return response;
    }

    public static LawSearchResponse error(String message) {
        return new LawSearchResponse(500, message, null, null);
    }

    public List<LawResult> getResults() {
        return results;
    }

    public void setResults(List<LawResult> results) {
        this.results = results;
    }

    public String getAiAdvice() {
        return aiAdvice;
    }

    public void setAiAdvice(String aiAdvice) {
        this.aiAdvice = aiAdvice;
    }

    public List<OfficialSource> getOfficialSources() { return officialSources; }
    public void setOfficialSources(List<OfficialSource> officialSources) { this.officialSources = officialSources; }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static class LawResult {
        private String lawName;
        private String article;
        private String content;

        public LawResult() {}

        public LawResult(String lawName, String article, String content) {
            this.lawName = lawName;
            this.article = article;
            this.content = content;
        }

        public String getLawName() {
            return lawName;
        }

        public void setLawName(String lawName) {
            this.lawName = lawName;
        }

        public String getArticle() {
            return article;
        }

        public void setArticle(String article) {
            this.article = article;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    public static class OfficialSource {
        private String name;
        private String description;
        private String url;
        public OfficialSource() {}
        public OfficialSource(String name, String description, String url) { this.name = name; this.description = description; this.url = url; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}
