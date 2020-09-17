package com.tnt.assignment.service;

import com.tnt.assignment.model.AggregatedResult;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

public interface AggregationService {
    /**
     * This method is responsible to make three different calls to pricing, track and shipments services and then aggregate the result and send it back in non blocking way
     *
     * @param pricing   list of country codes to call pricing
     * @param track     list of ids to call track
     * @param shipments list of ids to call shipments
     * @return aggregated result
     */
    Mono<AggregatedResult> sendThenAggregate(Optional<List<String>> pricing, Optional<List<String>> track, Optional<List<String>> shipments);
}
