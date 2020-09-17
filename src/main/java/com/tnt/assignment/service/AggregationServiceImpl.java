package com.tnt.assignment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tnt.assignment.model.AggregatedResult;
import com.tnt.assignment.model.AggregationProperties;
import com.tnt.assignment.model.Mutable;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AggregationServiceImpl implements AggregationService {

    private WebClient pricingWebClient;
    private WebClient trackWebClient;
    private WebClient shipmentsWebClient;


    @Autowired
    private AggregationProperties aggregationProperties;

    private final UnicastProcessor<String> pricingProcessor;
    private final UnicastProcessor<String> trackProcessor;
    private final UnicastProcessor<String> shipmentsProcessor;

    public Flux<Map<String, Optional<String>>> sharedPricingOutputFlux;
    private final Flux<Map<String, Optional<String>>> sharedTrackOutputFlux;
    private final Flux<Map<String, Optional<List<String>>>> sharedShipmentsOutputFlux;

    private final FluxSink<String> fluxSinkPricingProcessor;
    private final FluxSink<String> fluxSinkTrackProcessor;
    private final FluxSink<String> fluxSinkShipmentsProcessor;

    private final ObjectMapper objectMapper;

    public AggregationServiceImpl() {
        pricingProcessor = new UnicastProcessor<>(new ArrayDeque<>());
        trackProcessor = new UnicastProcessor<>(new ArrayDeque<>());
        shipmentsProcessor = new UnicastProcessor<>(new ArrayDeque<>());

        //Here we use bufferTimeout to queue requested items and make a call when we have 5 items or when oldest item has already wait 5 seconds
        //We call share to make sure we can share the Flux with multiple subscribers
        sharedPricingOutputFlux = pricingProcessor.bufferTimeout(5, Duration.ofSeconds(5)).flatMap(this::getPricing).share().log();
        sharedTrackOutputFlux = trackProcessor.bufferTimeout(5, Duration.ofSeconds(5)).flatMap(this::getTrack).share().log();
        sharedShipmentsOutputFlux = shipmentsProcessor.bufferTimeout(5, Duration.ofSeconds(5)).flatMap(this::getShipments).share().log();

        //We call subscribe() to make sure we always have at least one subscriber even if all subscribers got disposed
        //This is because if all subscribers on a Flux have canceled it will cancel the source
        sharedPricingOutputFlux.subscribe();
        sharedTrackOutputFlux.subscribe();
        sharedShipmentsOutputFlux.subscribe();

        //To run processor subscribe and publish on different thread as it is allowed
        pricingProcessor.subscribeOn(Schedulers.elastic());
        trackProcessor.subscribeOn(Schedulers.elastic());
        shipmentsProcessor.subscribeOn(Schedulers.elastic());

        pricingProcessor.publishOn(Schedulers.elastic());
        trackProcessor.publishOn(Schedulers.elastic());
        shipmentsProcessor.publishOn(Schedulers.elastic());

        //We keep each FluxSink that is a safely gates multi-threaded producer
        fluxSinkPricingProcessor = pricingProcessor.sink();
        fluxSinkTrackProcessor = trackProcessor.sink();
        fluxSinkShipmentsProcessor = shipmentsProcessor.sink();

        objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void setUp() {
        pricingWebClient = WebClient.create(aggregationProperties.getPricingBaseUrl());
        trackWebClient = WebClient.create(aggregationProperties.getTrackBaseUrl());
        shipmentsWebClient = WebClient.create(aggregationProperties.getShipmentsBaseUrl());
    }

    public Mono<AggregatedResult> sendThenAggregate(Optional<List<String>> pricing, Optional<List<String>> track, Optional<List<String>> shipments) {
        //Merge given Monos into a new Mono that will be fulfilled when all of the given Monos have produced an item
        Mono output = Mono.zip(createResultMono(pricing, fluxSinkPricingProcessor, sharedPricingOutputFlux),
                createResultMono(track, fluxSinkTrackProcessor, sharedTrackOutputFlux),
                createResultMono(shipments, fluxSinkShipmentsProcessor, sharedShipmentsOutputFlux))
                .map(tuple -> new AggregatedResult(tuple.getT1(), tuple.getT2(), tuple.getT3()));
        return output;
    }

    private <T> Mono<Map<String, T>> createResultMono(Optional<List<String>> optionalInput, FluxSink<String> sink, Flux<Map<String, T>> outputFlux) {
        return optionalInput.filter(l -> !l.isEmpty()).map(input -> Mono.<Map<String, T>>create(emitter -> {
            //This Mutable is going to keep the reference of subscriber to dispose in inside lambda function
            Mutable<Disposable> disposable = new Mutable<>();
            //This map does not need to thread-safe because this stream will wait for consumption of current value before sending next!
            Map<String, T> aggregatedResult = new HashMap<>();
            disposable.setValue(Flux.from(outputFlux)
                    //Filtering values which is match with our request
                    .map(result -> (Map<String, T>) result.entrySet().stream().filter(entry -> input.contains(entry.getKey())).collect(HashMap<String, T>::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll))
                    .filter(result -> !result.isEmpty())
                    .subscribe(result -> {
                        aggregatedResult.putAll(result);
                        log.info("Receive data request={}, result={}, aggregated={}", toJson(input), toJson(result), toJson(aggregatedResult));
                        if (aggregatedResult.size() == input.size()) {
                            //We emit the Map we have created as a single value of this created Mono as we have visited all our items' result
                            emitter.success(aggregatedResult);
                            log.info("Emitter has sent the data.");
                            if (!disposable.getValue().isDisposed()) {
                                //We dispose this subscription as we have visited all our requested items' result and next values will not belong to us
                                disposable.getValue().dispose();
                                log.info("Subscriber has been disposed.");
                            }

                        }
                    }));
            //We send our requested items to FluxSink at doOnSubscribe of our created Mono to make sure our subscription function will be executed for each return value and we do not miss any value because of timing
        }).doOnSubscribe(subscription -> input.stream().forEach(i -> sink.next(i))).subscribeOn(Schedulers.elastic()))
                .orElse(Mono.just(new HashMap<>()));
    }

    private Mono<Map<String, Optional<String>>> getPricing(List<String> ids) {
        return pricingWebClient.get()
                .uri(aggregationProperties.getPricingUri(), ids.stream().sorted().collect(Collectors.joining(",")))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Optional<String>>>() {
                })
                .timeout(aggregationProperties.getTimeout())
                .onErrorReturn(e -> e instanceof WebClientResponseException || e instanceof TimeoutException, ids.stream().collect(Collectors.toMap(Function.identity(), v -> Optional.empty())));
    }

    private Mono<Map<String, Optional<String>>> getTrack(List<String> ids) {
        return trackWebClient.get()
                .uri(aggregationProperties.getTrackUri(), ids.stream().sorted().collect(Collectors.joining(",")))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Optional<String>>>() {
                })
                .timeout(aggregationProperties.getTimeout())
                .onErrorReturn(e -> e instanceof WebClientResponseException || e instanceof TimeoutException, ids.stream().collect(Collectors.toMap(Function.identity(), v -> Optional.empty())));
    }

    private Mono<Map<String, Optional<List<String>>>> getShipments(List<String> ids) {
        return shipmentsWebClient.get()
                .uri(aggregationProperties.getShipmentsUri(), ids.stream().sorted().collect(Collectors.joining(",")))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Optional<List<String>>>>() {
                })
                .timeout(aggregationProperties.getTimeout())
                .onErrorReturn(e -> e instanceof WebClientResponseException || e instanceof TimeoutException, ids.stream().collect(Collectors.toMap(Function.identity(), v -> Optional.empty())));
    }

    @SneakyThrows
    private String toJson(Object obj) {
        return objectMapper.writeValueAsString(obj);
    }
}
