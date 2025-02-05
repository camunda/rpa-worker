package io.camunda.rpa.worker.files.api;

import jakarta.validation.constraints.NotBlank;

record StoreFilesRequest(@NotBlank String files) { }
