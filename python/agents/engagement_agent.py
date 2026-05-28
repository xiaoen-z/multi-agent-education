"""
Engagement Agent（互动Agent）-- 学习状态监测与自适应干预。

核心职责：
1. 监测学生学习行为指标（响应时间、错误率趋势、会话时长）
2. 基于行为分析判断学习状态（专注/挫败/厌倦/疲劳）
3. 适时发出干预事件：鼓励、建议休息、调整节奏

面试要点：
- 行为特征工程：如何从原始数据提取有意义的特征
- 状态机模型：学习状态之间的转换条件
- 干预策略的A/B测试思路
"""

import logging
from datetime import datetime
from enum import Enum

from .base_agent import BaseAgent
from core.event_bus import Event, EventType

logger = logging.getLogger(__name__)


class LearningState(str, Enum):
    FOCUSED = "focused"  # 专注学习中
    STRUGGLING = "struggling"  # 遇到困难
    FRUSTRATED = "frustrated"  # 明显挫败
    BORED = "bored"  # 可能无聊
    FATIGUED = "fatigued"  # 学习疲劳
    IDLE = "idle"  # 长时间无操作


class LearnerEngagement:
    """单个学习者的互动状态跟踪。"""

    def __init__(self, learner_id: str):
        self.learner_id = learner_id
        self.state = LearningState.FOCUSED
        self.recent_response_times: list[float] = []
        self.recent_results: list[bool] = []  # True=正确
        self.session_start = datetime.now()
        self.last_activity = datetime.now()
        self.consecutive_errors = 0
        self.consecutive_correct = 0
        self.total_interactions = 0
        self.encouragement_count = 0

    @property
    def session_duration_minutes(self) -> float:
        return (datetime.now() - self.session_start).total_seconds() / 60

    @property
    def idle_seconds(self) -> float:
        return (datetime.now() - self.last_activity).total_seconds()

    @property
    def recent_accuracy(self) -> float:
        recent = self.recent_results[-10:]
        return sum(recent) / max(1, len(recent)) if recent else 0.5

    @property
    def avg_response_time(self) -> float:
        recent = self.recent_response_times[-10:]
        return sum(recent) / max(1, len(recent)) if recent else 0.0


class EngagementAgent(BaseAgent):
    """互动Agent：监测学习状态，适时干预。"""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._engagements: dict[str, LearnerEngagement] = {}

    @property
    def subscribed_events(self) -> list[EventType]:
        return [
            EventType.STUDENT_SUBMISSION,
            EventType.ASSESSMENT_COMPLETE,
            EventType.STUDENT_MESSAGE,
        ]

    def _get_engagement(self, learner_id: str) -> LearnerEngagement:
        if learner_id not in self._engagements:
            self._engagements[learner_id] = LearnerEngagement(learner_id)
        return self._engagements[learner_id]

    async def handle_event(self, event: Event) -> None:
        if event.type == EventType.STUDENT_SUBMISSION:
            await self._track_submission(event)
        elif event.type == EventType.ASSESSMENT_COMPLETE:
            await self._analyze_engagement(event)
        elif event.type == EventType.STUDENT_MESSAGE:
            await self._track_activity(event)

    async def _track_submission(self, event: Event) -> None:
        """追踪答题提交行为。"""
        eng = self._get_engagement(event.learner_id)
        is_correct = event.data.get("is_correct", False)
        time_spent = event.data.get("time_spent_seconds", 0)

        eng.last_activity = datetime.now()
        eng.total_interactions += 1
        eng.recent_results.append(is_correct)
        if time_spent > 0:
            eng.recent_response_times.append(time_spent)

        if is_correct:
            eng.consecutive_correct += 1
            eng.consecutive_errors = 0
        else:
            eng.consecutive_errors += 1
            eng.consecutive_correct = 0

        # 保持最近记录窗口大小
        if len(eng.recent_results) > 20:
            eng.recent_results = eng.recent_results[-20:]
        if len(eng.recent_response_times) > 20:
            eng.recent_response_times = eng.recent_response_times[-20:]

    async def _track_activity(self, event: Event) -> None:
        """追踪学生活动。"""
        eng = self._get_engagement(event.learner_id)
        eng.last_activity = datetime.now()
        eng.total_interactions += 1

    async def _analyze_engagement(self, event: Event) -> None:
        """分析学习状态，决定是否干预。"""
        eng = self._get_engagement(event.learner_id)
        old_state = eng.state
        new_state = self._detect_state(eng)
        eng.state = new_state

        if new_state != old_state:
            logger.info(
                "[EngagementAgent] learner=%s state: %s -> %s",
                event.learner_id,
                old_state.value,
                new_state.value,
            )

        if new_state == LearningState.FRUSTRATED:
            await self._intervene_frustration(event.learner_id, eng)
        elif new_state == LearningState.BORED:
            await self._intervene_boredom(event.learner_id, eng)
        elif new_state == LearningState.FATIGUED:
            await self._intervene_fatigue(event.learner_id, eng)
        elif new_state == LearningState.FOCUSED and eng.consecutive_correct >= 3:
            await self._encourage(event.learner_id, eng)

    def _detect_state(self, eng: LearnerEngagement) -> LearningState:
        """
        学习状态检测算法。

        基于多个行为指标综合判断：
        - 连续错误 ≥ 3 → FRUSTRATED
        - 近期正确率 > 0.9 且 连续正确 ≥ 5 → BORED
        - 会话时长 > 45分钟 且 正确率下降 → FATIGUED
        - 闲置时间 > 300秒 → IDLE
        - 连续错误 ≥ 1 但 < 3 → STRUGGLING
        - 默认 → FOCUSED
        """
        if eng.idle_seconds > 300:
            return LearningState.IDLE

        if eng.consecutive_errors >= 3:
            return LearningState.FRUSTRATED

        if eng.session_duration_minutes > 45 and eng.recent_accuracy < 0.5:
            return LearningState.FATIGUED

        if eng.recent_accuracy > 0.9 and eng.consecutive_correct >= 5:
            return LearningState.BORED

        if eng.consecutive_errors >= 1:
            return LearningState.STRUGGLING

        return LearningState.FOCUSED

    async def _intervene_frustration(self, learner_id: str, eng: LearnerEngagement) -> None:
        """挫败干预：鼓励 + 通知降低难度。"""
        message = await self._llm_message(
            "学生连续答错多题，感到挫败。请用温暖鼓励的语气发一条简短消息（1-2句），"
            "帮助学生重建信心。不要用模板化的套话。"
        )
        await self.emit(
            EventType.ENGAGEMENT_ALERT,
            learner_id,
            {
                "alert_type": "frustration",
                "consecutive_errors": eng.consecutive_errors,
                "recent_accuracy": eng.recent_accuracy,
                "message": message,
            },
        )
        await self.emit(
            EventType.PACE_ADJUSTMENT,
            learner_id,
            {"action": "slow_down", "reason": "frustration_detected"},
        )

    async def _intervene_boredom(self, learner_id: str, eng: LearnerEngagement) -> None:
        """无聊干预：建议进阶 + 通知提高难度。"""
        message = await self._llm_message(
            "学生连续答对很多题，正确率很高，可能感到无聊。请用活泼的语气鼓励，"
            "并自然建议挑战更难的内容（1-2句）。"
        )
        await self.emit(
            EventType.ENGAGEMENT_ALERT,
            learner_id,
            {
                "alert_type": "boredom",
                "consecutive_correct": eng.consecutive_correct,
                "recent_accuracy": eng.recent_accuracy,
                "message": message,
            },
        )
        await self.emit(
            EventType.PACE_ADJUSTMENT,
            learner_id,
            {"action": "speed_up", "reason": "boredom_detected"},
        )

    async def _intervene_fatigue(self, learner_id: str, eng: LearnerEngagement) -> None:
        """疲劳干预：建议休息。"""
        minutes = eng.session_duration_minutes
        message = await self._llm_message(
            f"学生已经连续学习了{minutes:.0f}分钟。请用关心的语气建议休息10-15分钟，"
            "简短自然（1-2句）。"
        )
        await self.emit(
            EventType.ENCOURAGEMENT,
            learner_id,
            {
                "message": message,
                "type": "fatigue_break",
                "session_minutes": minutes,
            },
        )

    async def _encourage(self, learner_id: str, eng: LearnerEngagement) -> None:
        """正向鼓励。"""
        if eng.encouragement_count % 3 == 0:
            message = await self._llm_message(
                f"学生连续{eng.consecutive_correct}题全对，表现很好。"
                "请用真诚的语气给予一句简短鼓励。"
            )
            await self.emit(
                EventType.ENCOURAGEMENT,
                learner_id,
                {
                    "message": message,
                    "type": "positive_streak",
                    "streak": eng.consecutive_correct,
                },
            )
        eng.encouragement_count += 1

    async def _llm_message(self, instruction: str) -> str:
        """生成干预消息，LLM 不可用时返回模板。"""
        if self.llm:
            result = await self.llm.chat(
                "你是一位教育陪伴者，消息要简短、自然、有温度，不超过3句话。",
                instruction,
                temperature=0.7,
            )
            if result:
                return result

        return self._fallback_message(instruction)

    def _fallback_message(self, instruction: str) -> str:
        """LLM 不可用时的模板消息。"""
        if "挫败" in instruction:
            return "别灰心！犯错是学习的一部分。每个人都会遇到困难的知识点，让我们换一个方式来学习。"
        if "无聊" in instruction:
            return "你表现得非常棒！看起来这些题目对你来说很简单了，让我们挑战更难的内容！"
        if "休息" in instruction:
            return "你已经学习很久了，非常努力！适当休息能提高学习效率，建议休息10-15分钟再继续。"
        return "继续保持，你的努力一定会有回报！"
