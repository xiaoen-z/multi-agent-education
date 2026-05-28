package com.edu.agent.agent;

import com.edu.agent.core.AgentEvent;
import com.edu.agent.core.EventType;
import com.edu.agent.core.SpacedRepetition;
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
 * Curriculum Agent -- 动态学习路径 + SM-2 间隔重复。
 */
@Component
public class CurriculumAgent {

    private static final Logger log = LoggerFactory.getLogger(CurriculumAgent.class);
    private final ApplicationEventPublisher publisher;
    private final SpacedRepetition sr = new SpacedRepetition();
    private final Map<String, Map<String, SpacedRepetition.ReviewItem>> reviewItems =
            new ConcurrentHashMap<>();

    public CurriculumAgent(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Async
    @EventListener(condition = "#event.type == T(com.edu.agent.core.EventType).MASTERY_UPDATED")
    public void handleMasteryUpdate(AgentEvent event) {
        String learnerId = event.getLearnerId();
        String knowledgeId = (String) event.getData().getOrDefault("knowledge_id", "");
        double mastery = ((Number) event.getData().getOrDefault("mastery", 0.0)).doubleValue();

        int quality = masteryToQuality(mastery);
        SpacedRepetition.ReviewItem item = getReviewItem(learnerId, knowledgeId);
        sr.review(item, quality);

        log.info("[Curriculum] learner={}, kp={}, EF={:.2f}, interval={:.1f}d",
                learnerId, knowledgeId, item.getEasinessFactor(), item.getIntervalDays());

        if (mastery >= 0.6) {
            Map<String, Object> data = new HashMap<>();
            data.put("knowledge_id", knowledgeId);
            data.put("reason", "mastery_threshold_reached");
            publisher.publishEvent(new AgentEvent(this, EventType.NEXT_TOPIC,
                    "CurriculumAgent", learnerId, data));
        }
    }

    @Async
    @EventListener(condition = "#event.type == T(com.edu.agent.core.EventType).WEAKNESS_DETECTED")
    public void handleWeakness(AgentEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("reason", "weakness_detected");
        data.put("weak_knowledge_id", event.getData().get("knowledge_id"));
        data.put("message", "检测到薄弱知识点，建议先复习前置知识");
        publisher.publishEvent(new AgentEvent(this, EventType.PATH_UPDATED,
                "CurriculumAgent", event.getLearnerId(), data));
    }

    private SpacedRepetition.ReviewItem getReviewItem(String learnerId, String knowledgeId) {
        return reviewItems
                .computeIfAbsent(learnerId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(knowledgeId, SpacedRepetition.ReviewItem::new);
    }

    private int masteryToQuality(double mastery) {
        if (mastery >= 0.9) return 5;
        if (mastery >= 0.75) return 4;
        if (mastery >= 0.6) return 3;
        if (mastery >= 0.4) return 2;
        if (mastery >= 0.2) return 1;
        return 0;
    }
}
