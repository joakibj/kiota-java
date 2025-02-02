package com.microsoft.kiota.store;

import com.microsoft.kiota.TriConsumer;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** In-memory implementation of the backing store. Allows for dirty tracking of changes. */
public class InMemoryBackingStore implements BackingStore {
    /** Creates a new instance of the backing store. */
    public InMemoryBackingStore() {
        // default constructor
    }

    private static class Pair<A, B> {
        private final A value0;
        private final B value1;

        public Pair(A value0, B value1) {
            this.value0 = value0;
            this.value1 = value1;
        }

        public A getValue0() {
            return value0;
        }

        public Pair<A, B> setValue0(A value0) {
            return new Pair(value0, value1);
        }

        public B getValue1() {
            return value1;
        }

        public Pair<A, B> setValue1(B value1) {
            return new Pair(value0, value1);
        }
    }

    private boolean isInitializationCompleted = true;
    private boolean returnOnlyChangedValues;
    private final Map<String, Pair<Boolean, Object>> store = new HashMap<>();
    private final Map<String, TriConsumer<String, Object, Object>> subscriptionStore =
            new HashMap<>();

    public void setIsInitializationCompleted(final boolean value) {
        this.isInitializationCompleted = value;
        for (final Map.Entry<String, Pair<Boolean, Object>> entry : this.store.entrySet()) {
            final Pair<Boolean, Object> wrapper = entry.getValue();
            final Pair<Boolean, Object> updatedValue = wrapper.setValue0(Boolean.valueOf(!value));
            entry.setValue(updatedValue);
        }
    }

    public boolean getIsInitializationCompleted() {
        return this.isInitializationCompleted;
    }

    public void setReturnOnlyChangedValues(final boolean value) {
        this.returnOnlyChangedValues = value;
    }

    public boolean getReturnOnlyChangedValues() {
        return this.returnOnlyChangedValues;
    }

    public void clear() {
        this.store.clear();
    }

    @Nonnull public Map<String, Object> enumerate() {
        final Map<String, Object> result = new HashMap<>();
        for (final Map.Entry<String, Pair<Boolean, Object>> entry : this.store.entrySet()) {
            final Pair<Boolean, Object> wrapper = entry.getValue();
            final Object value = this.getValueFromWrapper(wrapper);

            if (value != null) {
                result.put(entry.getKey(), wrapper.getValue1());
            }
        }
        return result;
    }

    @Nonnull public Iterable<String> enumerateKeysForValuesChangedToNull() {
        final List<String> result = new ArrayList<>();
        for (final Map.Entry<String, Pair<Boolean, Object>> entry : this.store.entrySet()) {
            final Pair<Boolean, Object> wrapper = entry.getValue();
            final Object value = wrapper.getValue1();
            if (value == null && wrapper.getValue0()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private Object getValueFromWrapper(final Pair<Boolean, Object> wrapper) {
        if (wrapper != null) {
            final Boolean hasChanged = wrapper.getValue0();
            if (!this.returnOnlyChangedValues
                    || (this.returnOnlyChangedValues
                            && hasChanged != null
                            && hasChanged.booleanValue())) {
                return wrapper.getValue1();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Nullable public <T> T get(@Nonnull final String key) {
        Objects.requireNonNull(key);
        final Pair<Boolean, Object> wrapper = this.store.get(key);
        final Object value = this.getValueFromWrapper(wrapper);
        try {
            return (T) value;
        } catch (ClassCastException ex) {
            return null;
        }
    }

    public <T> void set(@Nonnull final String key, @Nullable final T value) {
        Objects.requireNonNull(key);
        final Pair<Boolean, Object> valueToAdd =
                new Pair(Boolean.valueOf(this.isInitializationCompleted), value);
        final Pair<Boolean, Object> oldValue = this.store.put(key, valueToAdd);
        for (final TriConsumer<String, Object, Object> callback : this.subscriptionStore.values()) {
            callback.accept(key, oldValue.getValue1(), value);
        }
    }

    public void unsubscribe(@Nonnull final String subscriptionId) {
        Objects.requireNonNull(subscriptionId);
        this.subscriptionStore.remove(subscriptionId);
    }

    @Nonnull public String subscribe(@Nonnull final TriConsumer<String, Object, Object> callback) {
        final String subscriptionId = UUID.randomUUID().toString();
        subscribe(subscriptionId, callback);
        return subscriptionId;
    }

    public void subscribe(
            @Nonnull final String subscriptionId,
            @Nonnull final TriConsumer<String, Object, Object> callback) {
        Objects.requireNonNull(callback);
        Objects.requireNonNull(subscriptionId);
        this.subscriptionStore.put(subscriptionId, callback);
    }
}
