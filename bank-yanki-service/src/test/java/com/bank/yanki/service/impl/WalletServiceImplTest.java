package com.bank.yanki.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.yanki.dto.WalletRequest;
import com.bank.yanki.event.DebitCardEventPayload;
import com.bank.yanki.exception.BusinessRuleException;
import com.bank.yanki.exception.ResourceNotFoundException;
import com.bank.yanki.mapper.YankiMapper;
import com.bank.yanki.model.DebitCardReplica;
import com.bank.yanki.model.DocumentType;
import com.bank.yanki.model.Wallet;
import com.bank.yanki.repository.DebitCardReplicaRepository;
import com.bank.yanki.repository.WalletRepository;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private DebitCardReplicaRepository debitCardReplicaRepository;

    private WalletServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WalletServiceImpl(walletRepository, debitCardReplicaRepository, new YankiMapper());
        lenient().when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> {
            Wallet wallet = invocation.getArgument(0);
            if (wallet.getId() == null) {
                wallet.setId("w1");
            }
            return Mono.just(wallet);
        });
    }

    private WalletRequest request(String document, String phone) {
        return WalletRequest.builder().documentType(DocumentType.DNI).documentNumber(document).phoneNumber(phone)
                .imei("356938035643809").email("ana@mail.com").build();
    }

    private Wallet wallet() {
        return Wallet.builder().id("w1").documentNumber("1").phoneNumber("911111111").balance(BigDecimal.ZERO)
                .build();
    }

    @Test
    void createStartsWithZeroBalanceAndRequiresUniqueDocumentAndPhone() {
        when(walletRepository.existsByDocumentNumber("1")).thenReturn(Mono.just(false));
        when(walletRepository.existsByPhoneNumber("911111111")).thenReturn(Mono.just(false));
        service.create(request("1", "911111111")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(wallet -> wallet.getBalance().signum() == 0 && wallet.getDebitCardNumber() == null);

        when(walletRepository.existsByDocumentNumber("2")).thenReturn(Mono.just(true));
        service.create(request("2", "922222222")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("documentNumber"));

        when(walletRepository.existsByDocumentNumber("3")).thenReturn(Mono.just(false));
        when(walletRepository.existsByPhoneNumber("933333333")).thenReturn(Mono.just(true));
        service.create(request("3", "933333333")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("phoneNumber"));
    }

    @Test
    void listFindUpdateAndDelete() {
        Wallet wallet = wallet();
        when(walletRepository.findAll()).thenReturn(Flux.just(wallet));
        when(walletRepository.findByPhoneNumber("911111111")).thenReturn(Mono.just(wallet));
        when(walletRepository.findById("w1")).thenReturn(Mono.just(wallet));
        when(walletRepository.findById("x")).thenReturn(Mono.empty());
        when(walletRepository.delete(wallet)).thenReturn(Mono.empty());

        service.findAll(null).test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.findAll("911111111").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.findById("x").test().awaitDone(1, TimeUnit.SECONDS).assertError(ResourceNotFoundException.class);
        service.update("w1", request("1", "911111111")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(updated -> "ana@mail.com".equals(updated.getEmail()));
        service.delete("w1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        verify(walletRepository, never()).existsByDocumentNumber(any());
    }

    @Test
    void linkOnlyActiveCardsFromTheReplicaAndUnlink() {
        when(walletRepository.findById("w1")).thenReturn(Mono.just(wallet()));
        when(debitCardReplicaRepository.findByCardNumber("5555")).thenReturn(Mono.just(
                DebitCardReplica.builder().cardNumber("5555").status("ACTIVE").build()));
        when(debitCardReplicaRepository.findByCardNumber("6666")).thenReturn(Mono.just(
                DebitCardReplica.builder().cardNumber("6666").status("INACTIVE").build()));
        when(debitCardReplicaRepository.findByCardNumber("7777")).thenReturn(Mono.empty());

        service.linkDebitCard("w1", "5555").test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(wallet -> "5555".equals(wallet.getDebitCardNumber()));
        service.linkDebitCard("w1", "6666").test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(BusinessRuleException.class);
        service.linkDebitCard("w1", "7777").test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(ResourceNotFoundException.class);
        service.unlinkDebitCard("w1").test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(wallet -> wallet.getDebitCardNumber() == null);
    }

    @Test
    void replicaUpsertAndDelete() {
        when(debitCardReplicaRepository.save(any(DebitCardReplica.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(debitCardReplicaRepository.deleteById("d1")).thenReturn(Mono.empty());

        service.upsertDebitCard(DebitCardEventPayload.builder().cardId("d1").cardNumber("5555").mainAccountId("a1")
                .status("ACTIVE").build()).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        service.deleteDebitCard("d1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
    }
}
