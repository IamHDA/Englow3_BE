package com.englow3.blog.dto.command;

public record StartImageUploadCommand(String contentType, long contentLength) {
}
