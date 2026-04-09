package com.minipay.mpps.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

@Component
@Slf4j
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Wrap the request and response so we can read their bodies without destroying the data
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            // Let the request proceed to your controllers
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long timeTaken = System.currentTimeMillis() - startTime;

            // Log the Request and Response
            String requestBody = getStringValue(requestWrapper.getContentAsByteArray(), request.getCharacterEncoding());
            String responseBody = getStringValue(responseWrapper.getContentAsByteArray(), response.getCharacterEncoding());

            log.info(
                    "API REQUEST: method={}, uri={}, status={}, timeTaken={}ms, requestBody={}, responseBody={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    timeTaken,
                    requestBody.isEmpty() ? "empty" : requestBody,
                    responseBody.isEmpty() ? "empty" : responseBody
            );

            // CRITICAL: You must copy the response body back to the real response, or the user gets a blank screen!
            responseWrapper.copyBodyToResponse();
        }
    }

    private String getStringValue(byte[] contentAsByteArray, String characterEncoding) {
        try {
            return new String(contentAsByteArray, characterEncoding).replaceAll("[\n\r\t]", "");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }
}