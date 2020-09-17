package com.tnt.assignment.controller;

import com.tnt.assignment.model.AggregatedResult;
import com.tnt.assignment.service.AggregationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/aggregation")
public class AggregationController {

    @Autowired
    private AggregationService aggregationService;

    @GetMapping
    Mono<AggregatedResult> get(@RequestParam Optional<List<String>> pricing, @RequestParam Optional<List<String>> track,
                               @RequestParam Optional<List<String>> shipments) {
        return aggregationService.sendThenAggregate(pricing, track, shipments);
    }
}
