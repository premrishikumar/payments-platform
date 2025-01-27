package com.payments.platform;

import com.payments.platform.idempotency.IdempotencyService;
import com.payments.platform.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    void shouldDetectDuplicatePayment() {
        String key = "test-idempotency-key-123";
        when(paymentRepository.existsByIdempotencyKey(key)).thenReturn(true);
        assertThat(idempotencyService.isDuplicate(key)).isTrue();
    }

    @Test
    void shouldAllowNewPayment() {
        String key = "new-unique-key-456";
        when(paymentRepository.existsByIdempotencyKey(key)).thenReturn(false);
        assertThat(idempotencyService.isDuplicate(key)).isFalse();
    }
}
