package io.leavesfly.alphaforge.application.service.signal;

import io.leavesfly.alphaforge.domain.model.entity.signal.DecisionSignalFeedback;
import io.leavesfly.alphaforge.domain.repository.signal.DecisionSignalFeedbackRepository;
import io.leavesfly.alphaforge.domain.repository.signal.DecisionSignalOutcomeRepository;
import io.leavesfly.alphaforge.domain.repository.signal.DecisionSignalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DecisionSignalOutcomeService 测试")
class DecisionSignalOutcomeServiceTest {

    @Mock
    private DecisionSignalRepository signalRepo;
    @Mock
    private DecisionSignalOutcomeRepository outcomeRepo;
    @Mock
    private DecisionSignalFeedbackRepository feedbackRepo;

    @InjectMocks
    private DecisionSignalOutcomeService service;

    @Test
    @DisplayName("saveFeedback 应持久化反馈")
    void saveFeedbackPersists() {
        when(feedbackRepo.findBySignalId(1L)).thenReturn(null);
        when(feedbackRepo.findBySignalId(1L)).thenReturn(new DecisionSignalFeedback());

        service.saveFeedback(1L, Map.of(
                "feedback_value", "accurate",
                "reason_code", "good_call",
                "note", "test",
                "source", "web"));

        verify(feedbackRepo).saveOrUpdate(any(DecisionSignalFeedback.class));
    }
}
