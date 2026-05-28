package com.edu.agent.agent;

import com.edu.agent.core.AgentEvent;
import com.edu.agent.core.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tutor Agent -- 苏格拉底式教学。
 * <p>
 * 根据学生mastery等级选择不同的教学策略。
 * 不直接给答案，通过反问引导思考。
 */
@Component
public class TutorAgent {

    private static final Logger log = LoggerFactory.getLogger(TutorAgent.class);
    private final ApplicationEventPublisher publisher;
    private final Map<String, Integer> studentAttempts = new ConcurrentHashMap<>();

    public TutorAgent(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Async
    @EventListener(condition = "#event.type == T(com.edu.agent.core.EventType).ASSESSMENT_COMPLETE")
    public void handleAssessment(AgentEvent event) {
        String learnerId = event.getLearnerId();
        String knowledgeId = (String) event.getData().getOrDefault("knowledge_id", "");
        String level = (String) event.getData().getOrDefault("level", "beginner");
        Boolean isCorrect = (Boolean) event.getData().get("is_correct");

        if (Boolean.FALSE.equals(isCorrect)) {
            String key = learnerId + ":" + knowledgeId;
            int attempts = studentAttempts.merge(key, 1, Integer::sum);
            if (attempts >= 2) {
                Map<String, Object> hintData = new HashMap<>();
                hintData.put("knowledge_id", knowledgeId);
                hintData.put("mastery", event.getData().get("mastery"));
                hintData.put("attempts", attempts);
                publisher.publishEvent(new AgentEvent(this, EventType.HINT_NEEDED,
                        "TutorAgent", learnerId, hintData));
                return;
            }
        }

        String response = generateSocraticResponse(knowledgeId, level, isCorrect);

        Map<String, Object> data = new HashMap<>();
        data.put("knowledge_id", knowledgeId);
        data.put("response", response);
        data.put("teaching_style", "socratic");
        data.put("difficulty_level", level);
        publisher.publishEvent(new AgentEvent(this, EventType.TEACHING_RESPONSE,
                "TutorAgent", learnerId, data));
    }

    @Async
    @EventListener(condition = "#event.type == T(com.edu.agent.core.EventType).ENGAGEMENT_ALERT")
    public void handleEngagementAlert(AgentEvent event) {
        String alertType = (String) event.getData().getOrDefault("alert_type", "");
        Map<String, Object> data = new HashMap<>();
        if ("frustration".equals(alertType)) {
            data.put("action", "decrease");
            data.put("message", "让我们换一个角度，从更简单的地方开始。");
        } else if ("boredom".equals(alertType)) {
            data.put("action", "increase");
            data.put("message", "让我给你一个更有挑战性的问题！");
        }
        if (!data.isEmpty()) {
            publisher.publishEvent(new AgentEvent(this, EventType.DIFFICULTY_ADJUSTED,
                    "TutorAgent", event.getLearnerId(), data));
        }
    }

    private String generateSocraticResponse(String knowledgeId, String level, Boolean isCorrect) {
        if (Boolean.TRUE.equals(isCorrect)) {
            return String.format(
                    "很好！你在「%s」上表现不错。你能用自己的话解释一下这个概念吗？", knowledgeId);
        } else if (Boolean.FALSE.equals(isCorrect)) {
            return String.format(
                    "没关系，让我们分析「%s」。这道题考查的是什么知识点？你卡在了哪一步？", knowledgeId);
        }
        return String.format("关于「%s」，你已经了解了哪些内容？试着说说你的理解。", knowledgeId);
    }
}
