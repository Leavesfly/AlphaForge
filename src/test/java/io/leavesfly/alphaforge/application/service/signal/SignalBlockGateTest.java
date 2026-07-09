package io.leavesfly.alphaforge.application.service.signal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisReport;
import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("幻觉硬门阻断信号落库")
class SignalBlockGateTest {

    @Test
    void blockSignalSkipsPersist() {
        DecisionSignalService signalService = mock(DecisionSignalService.class);
        SignalExtractionService extraction = new SignalExtractionService(new ObjectMapper(), signalService);

        AnalysisReport report = new AnalysisReport();
        report.setId(1L);
        report.setStockCode("600519");
        report.setStockName("茅台");

        AnalysisResult result = new AnalysisResult();
        result.signal = "buy";
        result.score = 80;
        result.blockSignal = true;

        extraction.persistDecisionSignal(report, result);
        verify(signalService, never()).extractFromReport(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void normalSignalPersists() {
        DecisionSignalService signalService = mock(DecisionSignalService.class);
        SignalExtractionService extraction = new SignalExtractionService(new ObjectMapper(), signalService);

        AnalysisReport report = new AnalysisReport();
        report.setId(1L);
        report.setStockCode("600519");
        report.setStockName("茅台");

        AnalysisResult result = new AnalysisResult();
        result.signal = "buy";
        result.score = 80;
        result.confidence = "高";
        result.blockSignal = false;

        extraction.persistDecisionSignal(report, result);
        verify(signalService, times(1)).extractFromReport(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
