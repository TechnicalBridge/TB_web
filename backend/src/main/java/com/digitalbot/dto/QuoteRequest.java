package com.digitalbot.dto;

import java.util.List;

public record QuoteRequest(String planId, String billing, Integer users, List<String> extras) {}
