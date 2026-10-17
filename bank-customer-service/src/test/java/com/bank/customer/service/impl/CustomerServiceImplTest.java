package com.bank.customer.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.customer.cache.CustomerCache;
import com.bank.customer.dto.CustomerRequest;
import com.bank.customer.event.CustomerEventPublisher;
import com.bank.customer.exception.BusinessRuleException;
import com.bank.customer.exception.ResourceNotFoundException;
import com.bank.customer.mapper.CustomerMapper;
import com.bank.customer.model.Customer;
import com.bank.customer.model.CustomerProfile;
import com.bank.customer.model.CustomerType;
import com.bank.customer.model.DocumentType;
import com.bank.customer.repository.CustomerRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerCache customerCache;

    @Mock
    private CustomerEventPublisher customerEventPublisher;

    private CustomerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CustomerServiceImpl(customerRepository, new CustomerMapper(), customerCache,
                customerEventPublisher);
        lenient().when(customerCache.findById(any())).thenReturn(Maybe.empty());
        lenient().when(customerCache.findByDocumentNumber(any())).thenReturn(Maybe.empty());
        lenient().when(customerCache.put(any())).thenReturn(Completable.complete());
        lenient().when(customerCache.evict(any(), any(String[].class))).thenReturn(Completable.complete());
        lenient().when(customerEventPublisher.publishUpserted(any())).thenReturn(Completable.complete());
        lenient().when(customerEventPublisher.publishDeleted(any())).thenReturn(Completable.complete());
    }

    private CustomerRequest request(CustomerType type, CustomerProfile profile, String document) {
        return CustomerRequest.builder()
                .type(type)
                .documentType(type == CustomerType.PERSONAL ? DocumentType.DNI : DocumentType.RUC)
                .documentNumber(document)
                .name("Ana Torres")
                .profile(profile)
                .build();
    }

    private Customer stored(String id, CustomerType type, CustomerProfile profile, String document) {
        return Customer.builder().id(id).type(type).documentType(DocumentType.DNI).documentNumber(document)
                .name("Ana Torres").profile(profile).createdAt(LocalDateTime.now()).build();
    }

    @Test
    void createSavesPersonalCustomerWithStandardProfileByDefault() {
        when(customerRepository.existsByDocumentNumber("123")).thenReturn(Mono.just(false));
        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> {
                    Customer saved = invocation.getArgument(0);
                    saved.setId("c1");
                    return Mono.just(saved);
                });

        service.create(request(CustomerType.PERSONAL, null, "123")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(response -> "c1".equals(response.getId())
                        && response.getProfile() == CustomerProfile.STANDARD);
    }

    @Test
    void createAcceptsVipForPersonalAndPymeForBusiness() {
        when(customerRepository.existsByDocumentNumber(any())).thenReturn(Mono.just(false));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.create(request(CustomerType.PERSONAL, CustomerProfile.VIP, "1")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(response -> response.getProfile() == CustomerProfile.VIP);
        service.create(request(CustomerType.BUSINESS, CustomerProfile.PYME, "2")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(response -> response.getProfile() == CustomerProfile.PYME);
    }

    @Test
    void createRejectsVipForBusinessCustomer() {
        service.create(request(CustomerType.BUSINESS, CustomerProfile.VIP, "1")).test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error instanceof BusinessRuleException
                        && error.getMessage().contains("VIP is not allowed for a BUSINESS"));
        verify(customerRepository, never()).save(any());
    }

    @Test
    void createRejectsPymeForPersonalCustomer() {
        service.create(request(CustomerType.PERSONAL, CustomerProfile.PYME, "1")).test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertError(BusinessRuleException.class);
    }

    @Test
    void createRejectsDuplicatedDocumentNumber() {
        when(customerRepository.existsByDocumentNumber("123")).thenReturn(Mono.just(true));

        service.create(request(CustomerType.PERSONAL, null, "123")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error instanceof BusinessRuleException
                        && error.getMessage().contains("already exists"));
        verify(customerRepository, never()).save(any());
    }

    @Test
    void findAllAndFindById() {
        Customer customer = stored("c1", CustomerType.PERSONAL, CustomerProfile.STANDARD, "1");
        when(customerRepository.findAll()).thenReturn(Flux.just(customer));
        when(customerRepository.findById("c1")).thenReturn(Mono.just(customer));

        service.findAll().test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.findById("c1").test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(response -> "c1".equals(response.getId()));
    }

    @Test
    void findByIdFailsWhenMissing() {
        when(customerRepository.findById("x")).thenReturn(Mono.empty());

        service.findById("x").test().awaitDone(1, TimeUnit.SECONDS).assertError(ResourceNotFoundException.class);
    }

    @Test
    void updateKeepsIdAndChangesData() {
        Customer existing = stored("c1", CustomerType.PERSONAL, CustomerProfile.STANDARD, "1");
        when(customerRepository.findById("c1")).thenReturn(Mono.just(existing));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        CustomerRequest update = request(CustomerType.PERSONAL, CustomerProfile.VIP, "1");
        update.setEmail("new@example.com");

        service.update("c1", update).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(response -> "c1".equals(response.getId())
                        && "new@example.com".equals(response.getEmail())
                        && response.getProfile() == CustomerProfile.VIP);
    }

    @Test
    void updateRejectsDocumentNumberOfAnotherCustomer() {
        when(customerRepository.findById("c1"))
                .thenReturn(Mono.just(stored("c1", CustomerType.PERSONAL, CustomerProfile.STANDARD, "1")));
        when(customerRepository.existsByDocumentNumber("2")).thenReturn(Mono.just(true));

        service.update("c1", request(CustomerType.PERSONAL, null, "2")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(BusinessRuleException.class);
    }

    @Test
    void updateAcceptsNewFreeDocumentNumber() {
        when(customerRepository.findById("c1"))
                .thenReturn(Mono.just(stored("c1", CustomerType.PERSONAL, CustomerProfile.STANDARD, "1")));
        when(customerRepository.existsByDocumentNumber("2")).thenReturn(Mono.just(false));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.update("c1", request(CustomerType.PERSONAL, null, "2")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(response -> "2".equals(response.getDocumentNumber()));
    }

    @Test
    void updateRejectsKeepingVipWhenTypeChangesToBusiness() {
        when(customerRepository.findById("c1"))
                .thenReturn(Mono.just(stored("c1", CustomerType.PERSONAL, CustomerProfile.VIP, "1")));

        service.update("c1", request(CustomerType.BUSINESS, null, "1")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(BusinessRuleException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void deleteRemovesExistingCustomer() {
        Customer existing = stored("c1", CustomerType.PERSONAL, CustomerProfile.STANDARD, "1");
        when(customerRepository.findById("c1")).thenReturn(Mono.just(existing));
        when(customerRepository.delete(existing)).thenReturn(Mono.empty());

        service.delete("c1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        verify(customerRepository).delete(existing);
    }

    @Test
    void profileRestrictions() {
        assertThat(CustomerProfile.STANDARD.isAllowedFor(CustomerType.BUSINESS)).isTrue();
        assertThat(CustomerProfile.VIP.isAllowedFor(CustomerType.PERSONAL)).isTrue();
        assertThat(CustomerProfile.VIP.isAllowedFor(CustomerType.BUSINESS)).isFalse();
        assertThat(CustomerProfile.PYME.isAllowedFor(CustomerType.BUSINESS)).isTrue();
        assertThat(CustomerProfile.PYME.isAllowedFor(CustomerType.PERSONAL)).isFalse();
    }

    // --- Parte III: cache Redis y eventos -------------------------------------

    @Test
    void findByIdServesFromCacheWithoutTouchingMongo() {
        when(customerCache.findById("c1"))
                .thenReturn(Maybe.just(stored("c1", CustomerType.PERSONAL, CustomerProfile.STANDARD, "1")));

        service.findById("c1").test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(response -> "c1".equals(response.getId()));
        verify(customerRepository, never()).findById(any(String.class));
    }

    @Test
    void findByIdOnCacheMissReadsMongoAndCachesTheResult() {
        Customer customer = stored("c1", CustomerType.PERSONAL, CustomerProfile.STANDARD, "1");
        when(customerRepository.findById("c1")).thenReturn(Mono.just(customer));

        service.findById("c1").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        verify(customerCache).put(customer);
    }

    @Test
    void findByDocumentNumberUsesCacheThenMongo() {
        Customer customer = stored("c1", CustomerType.PERSONAL, CustomerProfile.STANDARD, "77");
        when(customerCache.findByDocumentNumber("77")).thenReturn(Maybe.just(customer));
        service.findByDocumentNumber("77").test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(response -> "77".equals(response.getDocumentNumber()));

        when(customerCache.findByDocumentNumber("88")).thenReturn(Maybe.empty());
        when(customerRepository.findByDocumentNumber("88")).thenReturn(Mono.just(customer));
        service.findByDocumentNumber("88").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        verify(customerCache).put(customer);

        when(customerRepository.findByDocumentNumber("99")).thenReturn(Mono.empty());
        service.findByDocumentNumber("99").test().awaitDone(1, TimeUnit.SECONDS).assertNoValues().assertComplete();
    }

    @Test
    void createCachesAndPublishesTheSnapshot() {
        when(customerRepository.existsByDocumentNumber("123")).thenReturn(Mono.just(false));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.create(request(CustomerType.PERSONAL, null, "123")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertComplete();
        verify(customerCache).put(any(Customer.class));
        verify(customerEventPublisher).publishUpserted(any(Customer.class));
    }

    @Test
    void createSucceedsEvenIfKafkaIsDown() {
        when(customerRepository.existsByDocumentNumber("123")).thenReturn(Mono.just(false));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(customerEventPublisher.publishUpserted(any()))
                .thenReturn(Completable.error(new IllegalStateException("broker down")));

        service.create(request(CustomerType.PERSONAL, null, "123")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertComplete().assertValueCount(1);
    }

    @Test
    void updateEvictsPreviousAndNewDocumentKeysAndPublishes() {
        when(customerRepository.findById("c1"))
                .thenReturn(Mono.just(stored("c1", CustomerType.PERSONAL, CustomerProfile.STANDARD, "1")));
        when(customerRepository.existsByDocumentNumber("2")).thenReturn(Mono.just(false));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.update("c1", request(CustomerType.PERSONAL, null, "2")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertComplete();
        verify(customerCache).evict("c1", "1", "2");
        verify(customerEventPublisher).publishUpserted(any(Customer.class));
    }

    @Test
    void deleteEvictsAndPublishesTheDeletion() {
        Customer existing = stored("c1", CustomerType.PERSONAL, CustomerProfile.STANDARD, "1");
        when(customerRepository.findById("c1")).thenReturn(Mono.just(existing));
        when(customerRepository.delete(existing)).thenReturn(Mono.empty());

        service.delete("c1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        verify(customerCache).evict("c1", "1");
        verify(customerEventPublisher).publishDeleted("c1");
    }
}
