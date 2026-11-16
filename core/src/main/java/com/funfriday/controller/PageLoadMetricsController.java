package com.funfriday.controller;

import com.funfriday.service.GameMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Accepts aggregate client timing for category catalogue availability. */
@RestController
@RequestMapping("/api/metrics")
@CrossOrigin(origins = {"http://localhost:3000", "http://funfriday.co.in", "https://funfriday.co.in"})
@RequiredArgsConstructor
public class PageLoadMetricsController {
    private final GameMetricsService gameMetricsService;

    @PostMapping("/category-catalogue-load")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordCategoryCatalogueLoad(@RequestBody CategoryCatalogueLoadRequest request) {
        gameMetricsService.recordCategoryCatalogueLoad(
                request.category() == null ? null : request.category().trim().toUpperCase(java.util.Locale.ROOT),
                request.durationMilliseconds()
        );
    }

    public record CategoryCatalogueLoadRequest(String category, long durationMilliseconds) { }
}
