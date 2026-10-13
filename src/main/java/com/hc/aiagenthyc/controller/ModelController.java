package com.hc.aiagenthyc.controller;

import com.hc.aiagenthyc.model.ModelCatalog;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/models")
public class ModelController {
    private final ModelCatalog catalog;
    public ModelController(ModelCatalog catalog) { this.catalog = catalog; }
    @GetMapping
    @Operation(summary = "查询当前可用模型和输入能力")
    public ModelCatalog.CatalogView models() { return catalog.view(); }
}
