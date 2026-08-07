package com.festival.budgetassist.estimate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MetadataController {

    private final MetadataService metadataService;

    MetadataController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @GetMapping("/api/v1/metadata")
    public MetadataResponse metadata() {
        return metadataService.getMetadata();
    }
}