package com.microsoft.kiota.serialization;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** ParseNode implementation for URI form encoded payloads */
public class FormParseNode implements ParseNode {
    private final String rawStringValue;
    private final HashMap<String, String> fields = new HashMap<>();
    /**
     * Initializes a new instance of the {@link FormParseNode} class.
     * @param rawString the raw string value to parse.
     */
    public FormParseNode(@Nonnull final String rawString) {
        Objects.requireNonNull(rawString, "parameter node cannot be null");
        rawStringValue = rawString;
        for (final String kv : rawString.split("&")) {
            final String[] split = kv.split("=");
            final String key = sanitizeKey(split[0]);
            if(split.length == 2) {
                if(fields.containsKey(key))
                    fields.get(key).concat("," + split[1].trim());
                else
                    fields.put(key, split[1].trim());
            }
        }
    }
    private String sanitizeKey(@Nonnull final String key) {
        Objects.requireNonNull(key);
        return key.trim();
    }
    @Nonnull
    public ParseNode getChildNode(@Nonnull final String identifier) {
        Objects.requireNonNull(identifier, "identifier parameter is required");
        final String key = sanitizeKey(identifier);
        if(fields.containsKey(key)) {
            final Consumer<Parsable> onBefore = this.onBeforeAssignFieldValues;
            final Consumer<Parsable> onAfter = this.onAfterAssignFieldValues;
            final FormParseNode result = new FormParseNode(fields.get(key));
            result.setOnBeforeAssignFieldValues(onBefore);
            result.setOnAfterAssignFieldValues(onAfter);
            return result;
        } else return null;
    }
    @Nullable
    public String getStringValue() {
        final String decoded = URLDecoder.decode(rawStringValue, StandardCharsets.UTF_8);
        if (decoded.equalsIgnoreCase("null")) return null;
        return decoded;
    }
    @Nullable
    public Boolean getBooleanValue() {
        switch(getStringValue().toLowerCase()) { //boolean parse returns false for any value that is not true
            case "true":
            case "1":
                return true;
            case "false":
            case "0":
                return false;
            default:
                return null;
        }
    }
    @Nullable
    public Byte getByteValue() {
        try {
            return Byte.parseByte(getStringValue());
        } catch (final NumberFormatException ex) {
            return null;
        }
    }
    @Nullable
    public Short getShortValue() {
        try {
            return Short.parseShort(getStringValue());
        } catch (final NumberFormatException ex) {
            return null;
        }
    }
    @Nullable
    public BigDecimal getBigDecimalValue() {
        try {
            return new BigDecimal(getStringValue());
        } catch (final NumberFormatException ex) {
            return null;
        }
    }
    @Nullable
    public Integer getIntegerValue() {
        try {
            return Integer.parseInt(getStringValue());
        } catch (final NumberFormatException ex) {
            return null;
        }
    }
    @Nullable
    public Float getFloatValue() {
        try {
            return Float.parseFloat(getStringValue());
        } catch (final NumberFormatException ex) {
            return null;
        }
    }
    @Nullable
    public Double getDoubleValue() {
        try {
            return Double.parseDouble(getStringValue());
        } catch (final NumberFormatException ex) {
            return null;
        }
    }
    @Nullable
    public Long getLongValue() {
        try {
            return Long.parseLong(getStringValue());
        } catch (final NumberFormatException ex) {
            return null;
        }
    }
    @Nullable
    public UUID getUUIDValue() {
        final String stringValue = getStringValue();
        if(stringValue == null) return null;
        return UUID.fromString(stringValue);
    }
    @Nullable
    public OffsetDateTime getOffsetDateTimeValue() {
        final String stringValue = getStringValue();
        if(stringValue == null) return null;
        return OffsetDateTime.parse(stringValue);
    }
    @Nullable
    public LocalDate getLocalDateValue() {
        final String stringValue = getStringValue();
        if(stringValue == null) return null;
        return LocalDate.parse(stringValue);
    }
    @Nullable
    public LocalTime getLocalTimeValue() {
        final String stringValue = getStringValue();
        if(stringValue == null) return null;
        return LocalTime.parse(stringValue);
    }
    @Nullable
    public Period getPeriodValue() {
        final String stringValue = getStringValue();
        if(stringValue == null) return null;
        return Period.parse(stringValue);
    }
    @Nullable
    public <T> List<T> getCollectionOfPrimitiveValues(@Nonnull final Class<T> targetClass) {
        throw new RuntimeException("deserialization of collections of is not supported with form encoding");
    }
    @Nullable
    public <T extends Parsable> List<T> getCollectionOfObjectValues(@Nonnull final ParsableFactory<T> factory) {
        throw new RuntimeException("deserialization of collections of is not supported with form encoding");
    }
    @Nullable
    public <T extends Enum<T>> List<T> getCollectionOfEnumValues(@Nonnull final Class<T> targetEnum) {
        Objects.requireNonNull(targetEnum, "parameter targetEnum cannot be null");
        final String stringValue = getStringValue();
        if(stringValue == null || stringValue.isEmpty()) {
            return null;
        } else {
            final String[] array = stringValue.split(",");
            final ArrayList<T> result = new ArrayList<>();
            for(final String item : array) {
                result.add(getEnumValueInt(item, targetEnum));
            }
            return result;
        }
    }
    @Nonnull
    public <T extends Parsable> T getObjectValue(@Nonnull final ParsableFactory<T> factory) {
        Objects.requireNonNull(factory, "parameter factory cannot be null");
        final T item = factory.Create(this);
        assignFieldValues(item, item.getFieldDeserializers());
        return item;
    }
    @Nullable
    public <T extends Enum<T>> T getEnumValue(@Nonnull final Class<T> targetEnum) {
        final String rawValue = this.getStringValue();
        if(rawValue == null || rawValue.isEmpty()) {
            return null;
        }
        return getEnumValueInt(rawValue, targetEnum);
    }
    @SuppressWarnings("unchecked")
    private <T extends Enum<T>> T getEnumValueInt(@Nonnull final String rawValue, @Nonnull final Class<T> targetEnum) {
        try {
            return (T)targetEnum.getMethod("forValue", String.class).invoke(null, rawValue);
        } catch (Exception ex) {
            return null;
        }
    }
    @Nullable
    public <T extends Enum<T>> EnumSet<T> getEnumSetValue(@Nonnull final Class<T> targetEnum) {
        final String rawValue = this.getStringValue();
        if(rawValue == null || rawValue.isEmpty()) {
            return null;
        }
        final EnumSet<T> result = EnumSet.noneOf(targetEnum);
        final String[] rawValues = rawValue.split(",");
        for (final String rawValueItem : rawValues) {
            final T value = getEnumValueInt(rawValueItem, targetEnum);
            if(value != null) {
                result.add(value);
            }
        }
        return result;
    }
    private <T extends Parsable> void assignFieldValues(final T item, final Map<String, Consumer<ParseNode>> fieldDeserializers) {
        if(!fields.isEmpty()) {
            if(this.onBeforeAssignFieldValues != null) {
                this.onBeforeAssignFieldValues.accept(item);
            }
            Map<String, Object> itemAdditionalData = null;
            if(item instanceof AdditionalDataHolder) {
                itemAdditionalData = ((AdditionalDataHolder)item).getAdditionalData();
            }
            for (final Map.Entry<String, String> fieldEntry : fields.entrySet()) {
                final String fieldKey = fieldEntry.getKey();
                final Consumer<ParseNode> fieldDeserializer = fieldDeserializers.get(fieldKey);
                final String fieldValue = fieldEntry.getValue();
                if(fieldValue == null)
                    continue;
                if(fieldDeserializer != null) {
                    final Consumer<Parsable> onBefore = this.onBeforeAssignFieldValues;
                    final Consumer<Parsable> onAfter = this.onAfterAssignFieldValues;
                    fieldDeserializer.accept(new FormParseNode(fieldValue) {{
                        this.setOnBeforeAssignFieldValues(onBefore);
                        this.setOnAfterAssignFieldValues(onAfter);
                    }});
                }
                else if (itemAdditionalData != null)
                    itemAdditionalData.put(fieldKey, fieldValue);
            }
            if(this.onAfterAssignFieldValues != null) {
                this.onAfterAssignFieldValues.accept(item);
            }
        }
    }
    @Nullable
    public Consumer<Parsable> getOnBeforeAssignFieldValues() {
        return this.onBeforeAssignFieldValues;
    }
    @Nullable
    public Consumer<Parsable> getOnAfterAssignFieldValues() {
        return this.onAfterAssignFieldValues;
    }
    private Consumer<Parsable> onBeforeAssignFieldValues;
    public void setOnBeforeAssignFieldValues(@Nullable final Consumer<Parsable> value) {
        this.onBeforeAssignFieldValues = value;
    }
    private Consumer<Parsable> onAfterAssignFieldValues;
    public void setOnAfterAssignFieldValues(@Nullable final Consumer<Parsable> value) {
        this.onAfterAssignFieldValues = value;
    }
    @Nullable
    public byte[] getByteArrayValue() {
        final String base64 = this.getStringValue();
        if(base64 == null || base64.isEmpty()) {
            return null;
        }
        return Base64.getDecoder().decode(base64);
    }
}
