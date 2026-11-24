package br.com.helpcsistemas.customeraccount.verification.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.helpcsistemas.customeraccount.verification.application.port.in.ReceiveProviderResultUseCase;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderCallbackInboxPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class CallbackInboxProcessorTest {

    private final ProviderCallbackInboxPort inbox = mock(ProviderCallbackInboxPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void claimsTheConfiguredBatchSize() {
        new CallbackInboxProcessor(inbox, List.of(), mock(ReceiveProviderResultUseCase.class), clock, 7).processDue();

        verify(inbox).claimDue(any(), eq(7));
    }

    @Test
    void rejectsNonPositiveBatchSize() {
        assertThatThrownBy(() -> new CallbackInboxProcessor(
                        inbox, List.of(), mock(ReceiveProviderResultUseCase.class), clock, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
