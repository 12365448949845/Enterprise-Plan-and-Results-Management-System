package com.planning.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AiRedactionService {

    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)(1\\d{2})\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{6})\\d{8}(\\d{3}[0-9Xx])(?![0-9Xx])");
    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern EMPLOYEE_NO = Pattern.compile("(?i)(员工编号|employeeNo|employee_no)\\s*[:：=]?\\s*[A-Za-z0-9_-]+");

    public JsonNode redact(JsonNode source) {
        return redactNode(source.deepCopy());
    }

    public String redactText(String source) {
        if (source == null) {
            return "";
        }
        return redactNode(TextNode.valueOf(source)).asText();
    }

    private JsonNode redactNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String lower = field.getKey().toLowerCase();
                if (lower.contains("mobile") || lower.contains("phone") || lower.contains("email")
                        || lower.contains("employeeno") || lower.contains("employee_no")) {
                    object.put(field.getKey(), "***");
                } else {
                    object.set(field.getKey(), redactNode(field.getValue()));
                }
            }
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) array.set(i, redactNode(array.get(i)));
            return array;
        }
        if (node.isTextual()) {
            String value = node.asText();
            value = MOBILE.matcher(value).replaceAll("$1****$2");
            value = ID_CARD.matcher(value).replaceAll("$1********$2");
            value = EMAIL.matcher(value).replaceAll("$1***$2");
            value = EMPLOYEE_NO.matcher(value).replaceAll("员工编号:***");
            return TextNode.valueOf(value);
        }
        return node;
    }
}
