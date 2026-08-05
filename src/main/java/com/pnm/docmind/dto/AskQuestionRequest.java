package com.pnm.docmind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;


public record AskQuestionRequest(@NotBlank @Size(max = 1000) String question) { }
