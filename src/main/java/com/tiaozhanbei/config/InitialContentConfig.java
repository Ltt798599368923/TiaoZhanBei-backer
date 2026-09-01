package com.tiaozhanbei.config;

import com.tiaozhanbei.entity.DocumentTemplate;
import com.tiaozhanbei.entity.SystemNotice;
import com.tiaozhanbei.repository.DocumentTemplateRepository;
import com.tiaozhanbei.repository.SystemNoticeRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.Arrays;

@Configuration
public class InitialContentConfig {
    @Bean
    public ApplicationRunner initialContent(DocumentTemplateRepository templateRepository,
                                            SystemNoticeRepository noticeRepository) {
        return args -> {
            if (templateRepository.countByIsDeletedFalse() == 0) {
                templateRepository.saveAll(Arrays.asList(
                        template("民事起诉状", "civil", "用于民事纠纷立案的基础起诉状参考模板。",
                                "民事起诉状\n\n原告：\n被告：\n\n诉讼请求：\n1. \n\n事实与理由：\n\n此致\n人民法院\n\n具状人：\n日期："),
                        template("劳动合同", "contract", "劳动关系建立、岗位职责和薪酬约定参考模板。",
                                "劳动合同\n\n甲方（用人单位）：\n乙方（劳动者）：\n\n一、合同期限\n二、工作内容和工作地点\n三、劳动报酬\n四、社会保险\n五、双方权利义务\n\n甲方签字（盖章）：\n乙方签字：\n日期："),
                        template("房屋租赁合同", "contract", "住宅租赁中租金、期限、维修责任的参考条款。",
                                "房屋租赁合同\n\n出租人：\n承租人：\n房屋地址：\n\n一、租赁期限\n二、租金及支付方式\n三、押金\n四、房屋使用和维修\n五、违约责任\n\n出租人签字：\n承租人签字：\n日期："),
                        template("刑事辩护意见", "criminal", "刑事案件阅卷、证据审查和辩护意见整理参考。",
                                "辩护意见\n\n案件名称：\n当事人：\n\n一、案件基本情况\n二、证据审查意见\n三、法律适用意见\n四、辩护请求\n\n辩护人：\n日期："),
                        template("行政复议申请书", "administrative", "对行政行为申请行政复议时的基础文书参考。",
                                "行政复议申请书\n\n申请人：\n被申请人：\n\n复议请求：\n事实和理由：\n\n此致\n行政复议机关\n\n申请人：\n日期："),
                        template("公司合作协议", "company", "企业合作范围、分工、保密与争议解决参考模板。",
                                "公司合作协议\n\n甲方：\n乙方：\n\n一、合作事项\n二、双方分工\n三、收益分配\n四、保密义务\n五、争议解决\n\n甲方签字（盖章）：\n乙方签字（盖章）：\n日期：")
                ));
            }

            if (noticeRepository.count() == 0) {
                noticeRepository.saveAll(Arrays.asList(
                        notice("欢迎使用法律咨询服务", "你可以使用 AI 法律咨询、法条检索、文书模板和合同审查功能。提交的咨询和合同记录可在个人中心查看。"),
                        notice("服务使用提醒", "平台提供的信息仅作普法和一般法律信息参考。涉及重大权益时，请及时咨询具备执业资质的律师。"),
                        notice("隐私保护说明", "请勿在咨询内容中提交身份证号、银行卡号、密码等敏感信息。我们仅在提供服务所需范围内处理你的资料。")
                ));
            }
        };
    }

    private DocumentTemplate template(String title, String category, String description, String content) {
        DocumentTemplate template = new DocumentTemplate();
        template.setTitle(title);
        template.setCategory(category);
        template.setDescription(description);
        template.setContent(content);
        return template;
    }

    private SystemNotice notice(String title, String content) {
        SystemNotice notice = new SystemNotice();
        notice.setTitle(title);
        notice.setContent(content);
        notice.setCreatedTime(LocalDateTime.now());
        return notice;
    }
}
