package com.ghouse.socialraven.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class JsonCustomConverter implements AttributeConverter<JsonNode, String> {

	ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String convertToDatabaseColumn(JsonNode node) {
		if(node == null) {
			return "{}";
		}
		return node.toString();
	}

	@Override
	public JsonNode convertToEntityAttribute(String data) {
		try {
			return objectMapper.readTree(data);
		} catch (Exception exp) {
			return null;
		}
	}
}