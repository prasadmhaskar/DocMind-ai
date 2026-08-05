package com.pnm.docmind.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmptyFile.class)
    public ResponseEntity<String> handleEmptyFileException(EmptyFile ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(InvalidFile.class)
    public ResponseEntity<String> handleInvalidFileException(InvalidFile ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(QuestionNotValidException.class)
    public ResponseEntity<String> handleQuestionNotValidException(QuestionNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<String> handleFileStorageException(FileStorageException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }

    @ExceptionHandler(PdfProcessingException.class)
    public ResponseEntity<String> handlePdfProcessingException(PdfProcessingException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ex.getMessage());
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<String> handleDocumentNotFoundException(DocumentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(DocumentProcessingException.class)
    public ResponseEntity<String> handleDocumentProcessingException(DocumentProcessingException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ex.getMessage());
    }




}
