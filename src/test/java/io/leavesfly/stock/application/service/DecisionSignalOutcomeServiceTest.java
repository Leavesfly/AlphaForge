package io.leavesfly.stock.application.service;

import io.leavesfly.stock.domain.model.entity.DecisionSignalFeedback;
import io.leavesfly.stock.infrastructure.persistence.DecisionSignalFeedbackRepository;
import io.leavesfly.stock.infrastructure.persistence.DecisionSignalOutcomeRepository;
import io.leavesfly.stock.infrastructure.persistence.DecisionSignalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
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
