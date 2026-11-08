package com.example.ccas.answer;

public class AnswerGenerationException extends RuntimeException {
    public AnswerGenerationException() {
        super("Could not generate a cited answer.");
    }
}
