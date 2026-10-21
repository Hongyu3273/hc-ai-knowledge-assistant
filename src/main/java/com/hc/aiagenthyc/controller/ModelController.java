package com.hc.aiagenthyc.controller;

import com.hc.aiagenthyc.model.ModelCatalog;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

/** Lists registered chat routes; registration does not verify remote provider availability. */
@RestController
@RequestMapping("/ai/models")
public class ModelController {
    private final ModelCatalog catalog;
    public ModelController(ModelCatalog catalog) { this.catalog = catalog; }
    @GetMapping
    @Operation(summary = "List currently available models and supported input types")
    public ModelCatalog.CatalogView models() { return catalog.view(); }
}
