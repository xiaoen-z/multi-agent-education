package com.edu.agent.agent;

import com.edu.agent.core.AgentEvent;
import com.edu.agent.core.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Engagement Agent -- 学习状态监测与自适应干预。
 */
@Component
public class EngagementAgent {

    private static final Logger log = LoggerFactory.getLogger(EngagementAgent.class);
    private final ApplicationEventPublisher publisher;
    private final Map<String, LearnerEngagement> engagements = new ConcurrentHashMap<>();

    public EngagementAgent(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Async
    @EventListener(condition = "#event.type == T(com.edu.agent.core.EventType).STUDENT_SUBMISSION")
    public void trackSubmission(AgentEvent event) {
        var eng = getEngagement(event.getLearnerId());
        boolean isCorrect = (boolean) event.getData().getOrDefault("is_correct", false);

        eng.lastActivity = Instant.now();
        eng.totalInteractions++;
        eng.recentResults.add(isCorrect);
        if (eng.recentResults.size() > 20) {
            eng.recentResults.removeFirst();
        }

        if (isCorrect) {
            eng.consecutiveCorrect++;
            eng.consecutiveErrors = 0;
        } else {
            eng.consecutiveErrors++;
            eng.consecutiveCorrect = 0;
        }
    }

    @Async
    @EventListener(condition = "#event.type == T(com.edu.agent.core.EventType).ASSESSMENT_COMPLETE")
    public void analyzeEngagement(AgentEvent event) {
        var eng = getEngagement(event.getLearnerId());
        String state = detectState(eng);

        if ("frustrated".equals(state)) {
            Map<String, Object> data = Map.of(
                    "alert_type", "frustration",
                    "consecutive_errors", eng.consecutiveErrors,
                    "message", "别灰心！犯错是学习的一部分。"
            );
            publisher.publishEvent(new AgentEvent(this, EventType.ENGAGEMENT_ALERT,
                    "EngagementAgent", event.getLearnerId(), data));
        } else if ("bored".equals(state)) {
            Map<String, Object> data = Map.of(
                    "alert_type", "boredom",
                    "message", "你表现非常棒！让我们挑战更难的内容！"
            );
            publisher.publishEvent(new AgentEvent(this, EventType.ENGAGEMENT_ALERT,
                    "EngagementAgent", event.getLearnerId(), data));
        } else if ("focused".equals(state) && eng.consecutiveCorrect >= 3) {
            Map<String, Object> data = Map.of(
                    "message", String.format("连续%d题全对！继续保持！", eng.consecutiveCorrect),
                    "type", "positive_streak"
            );
            publisher.publishEvent(new AgentEvent(this, EventType.ENCOURAGEMENT,
                    "EngagementAgent", event.getLearnerId(), data));
        }
    }

    private String detectState(LearnerEngagement eng) {
        if (eng.consecutiveErrors >= 3) return "frustrated";
        long recent = eng.recentResults.stream().filter(b -> b).count();
        double accuracy = eng.recentResults.isEmpty() ? 0.5 :
                (double) recent / eng.recentResults.size();
        if (accuracy > 0.9 && eng.consecutiveCorrect >= 5) return "bored";
        if (eng.consecutiveErrors >= 1) return "struggling";
        return "focused";
    }

    private LearnerEngagement getEngagement(String learnerId) {
        return engagements.computeIfAbsent(learnerId, LearnerEngagement::new);
    }

    static class LearnerEngagement {
        String learnerId;
        Instant sessionStart = Instant.now();
        Instant lastActivity = Instant.now();
        LinkedList<Boolean> recentResults = new LinkedList<>();
        int consecutiveErrors = 0;
        int consecutiveCorrect = 0;
        int totalInteractions = 0;

        LearnerEngagement(String learnerId) {
            this.learnerId = learnerId;
        }
    }
}
