package com.edu.agent.controller;

import com.edu.agent.agent.AssessmentAgent;
import com.edu.agent.core.AgentEvent;
import com.edu.agent.core.EventType;
import com.edu.agent.core.LearnerModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST 控制器 -- 对外暴露API。
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class EduController {

    private final ApplicationEventPublisher publisher;
    private final AssessmentAgent assessmentAgent;

    public EduController(ApplicationEventPublisher publisher, AssessmentAgent assessmentAgent) {
        this.publisher = publisher;
        this.assessmentAgent = assessmentAgent;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "service", "multi-agent-education-java", "agents", 5);
    }

    @PostMapping("/submit")
    public Map<String, Object> submitAnswer(@RequestBody Map<String, Object> body) {
        String learnerId = (String) body.get("learner_id");
        String knowledgeId = (String) body.get("knowledge_id");
        boolean isCorrect = (boolean) body.get("is_correct");

        Map<String, Object> data = new HashMap<>();
        data.put("knowledge_id", knowledgeId);
        data.put("is_correct", isCorrect);
        data.put("time_spent_seconds", body.getOrDefault("time_spent_seconds", 0));

        publisher.publishEvent(new AgentEvent(this, EventType.STUDENT_SUBMISSION,
                "api", learnerId, data));

        return Map.of("status", "processed", "learner_id", learnerId);
    }

    @PostMapping("/question")
    public Map<String, Object> askQuestion(@RequestBody Map<String, Object> body) {
        String learnerId = (String) body.get("learner_id");
        Map<String, Object> data = new HashMap<>();
        data.put("knowledge_id", body.getOrDefault("knowledge_id", "general"));
        data.put("question", body.getOrDefault("question", ""));

        publisher.publishEvent(new AgentEvent(this, EventType.STUDENT_QUESTION,
                "api", learnerId, data));
        return Map.of("status", "processed");
    }

    @GetMapping("/progress/{learnerId}")
    public Map<String, Object> getProgress(@PathVariable String learnerId) {
        LearnerModel model = assessmentAgent.getLearnerModels().get(learnerId);
        if (model == null) {
            return Map.of("learner_id", learnerId, "status", "no_data");
        }
        return Map.of("learner_id", learnerId, "progress", model.getOverallProgress());
    }
}
