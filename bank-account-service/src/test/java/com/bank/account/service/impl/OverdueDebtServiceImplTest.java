package com.bank.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.account.event.DebtEventPayload;
import com.bank.account.exception.BusinessRuleException;
import com.bank.account.model.OverdueDebt;
import com.bank.account.repository.OverdueDebtRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class OverdueDebtServiceImplTest {

    @Mock
    private OverdueDebtRepository overdueDebtRepository;

    @InjectMocks
    private OverdueDebtServiceImpl service;

    private final DebtEventPayload detected = DebtEventPayload.builder().customerId("c1").productType("CREDIT")
            .productId("k1").dueDate(LocalDate.of(2026, 10, 1)).dueAmount(new BigDecimal("150")).build();

    @Test
    void registerCreatesTheLocalCopyWhenItDoesNotExist() {
        when(overdueDebtRepository.findByProductTypeAndProductId("CREDIT", "k1")).thenReturn(Mono.empty());
        when(overdueDebtRepository.save(any(OverdueDebt.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.registerOverdue(detected).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        ArgumentCaptor<OverdueDebt> captor = ArgumentCaptor.forClass(OverdueDebt.class);
        verify(overdueDebtRepository).save(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isEqualTo("c1");
        assertThat(captor.getValue().getDueAmount()).isEqualByComparingTo("150");
    }

    @Test
    void registeringTheSameDebtAgainUpdatesTheSameDocument() {
        OverdueDebt existing = OverdueDebt.builder().id("d1").productType("CREDIT").productId("k1")
                .dueAmount(BigDecimal.TEN).build();
        when(overdueDebtRepository.findByProductTypeAndProductId("CREDIT", "k1")).thenReturn(Mono.just(existing));
        when(overdueDebtRepository.save(any(OverdueDebt.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.registerOverdue(detected).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        assertThat(existing.getId()).isEqualTo("d1");
        assertThat(existing.getDueAmount()).isEqualByComparingTo("150");
    }

    @Test
    void clearDeletesTheProductDebt() {
        when(overdueDebtRepository.deleteByProductTypeAndProductId("CREDIT", "k1")).thenReturn(Mono.just(1L));

        service.clearOverdue(detected).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        verify(overdueDebtRepository).deleteByProductTypeAndProductId("CREDIT", "k1");
    }

    @Test
    void acquisitionRuleBlocksCustomersWithOverdueDebtAndLetsOthersThrough() {
        when(overdueDebtRepository.existsByCustomerId("c1")).thenReturn(Mono.just(true));
        when(overdueDebtRepository.existsByCustomerId("c2")).thenReturn(Mono.just(false));

        service.ensureNoOverdueDebt("c1").test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error instanceof BusinessRuleException
                        && error.getMessage().contains("overdue debt"));
        service.ensureNoOverdueDebt("c2").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
    }
}
