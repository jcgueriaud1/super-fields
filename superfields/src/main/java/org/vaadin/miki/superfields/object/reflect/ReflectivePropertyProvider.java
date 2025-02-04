package org.vaadin.miki.superfields.object.reflect;

import com.vaadin.flow.function.SerializableBiConsumer;
import com.vaadin.flow.function.SerializableFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vaadin.miki.superfields.object.Property;
import org.vaadin.miki.superfields.object.PropertyProvider;
import org.vaadin.miki.util.ReflectTools;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Simple reflection-based definition provider.
 * It scans a given type for {@code private} fields (including {@code final}) and corresponding setters and/or getters.
 * Delegates the scanning to {@link ReflectTools#extractFieldsWithMethods(Class, boolean)}.
 * Superclasses are by default included, unless {@link DoNotScanSuperclasses} is used on the type.
 * All fields are included in the results, except fields annotated with {@link Ignore}.
 * The declared field types are used, unless {@link UseActualType} is used, in which case the actual type of the field's value is used.
 * The results are cached, except when a type has {@link UseActualType} annotation on any of its fields.
 *
 * @author miki
 * @since 2022-06-03
 */
public class ReflectivePropertyProvider implements PropertyProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReflectivePropertyProvider.class);

    private final Map<Class<?>, List<Property<?, ?>>> cache = new HashMap<>();
    private final Set<Class<?>> notCachedTypes = new HashSet<>();

    private final List<MetadataProvider> metadataProviders = new ArrayList<>();

    private boolean usingFakeSettersWhenNotPresent = false;
    private boolean usingFakeGettersWhenNotPresent = false;

    @Override
    @SuppressWarnings("unchecked") // should be fine
    public <T> List<Property<T, ?>> getObjectPropertyDefinitions(Class<T> type, T instance) {
        if(instance != null)
            type = (Class<T>) instance.getClass();
        // cache will contain the type anyway, but this explicitly forces recreating the definition
        if(this.notCachedTypes.contains(type))
            return this.buildProperties(type, instance);
        else return (List<Property<T,?>>)(List<?>) this.cache.computeIfAbsent(type, t -> (List<Property<?,?>>)(List<?>) this.buildProperties(t, instance));
    }

    private <T> List<Property<T, ?>> buildProperties(Class<T> type, Object instance) {
        return ReflectTools.extractFieldsWithMethods(type, type.isAnnotationPresent(DoNotScanSuperclasses.class)).entrySet()
                .stream()
                .filter(fieldEntry -> !fieldEntry.getKey().isAnnotationPresent(Ignore.class))
                .map(fieldEntry -> {
                    Object fieldValue = null;
                    if(fieldEntry.getKey().isAnnotationPresent(UseActualType.class)) {
                        try {
                            this.notCachedTypes.add(type); // do not cache the type
                            fieldValue = instance == null || fieldEntry.getValue()[ReflectTools.GETTER_INDEX] == null ? null : fieldEntry.getValue()[ReflectTools.GETTER_INDEX].invoke(instance);

                        } catch (IllegalAccessException | InvocationTargetException e) {
                            LOGGER.warn("could not determine the actual type for field {}.{}; using declared type {} instead", type, fieldEntry.getKey().getName(), fieldEntry.getKey().getType().getSimpleName(), e);
                        }
                    }
                    return this.buildDefinition(type, fieldEntry.getKey(), fieldValue == null ? fieldEntry.getKey().getType() : fieldValue.getClass(), fieldEntry.getValue()[ReflectTools.GETTER_INDEX], fieldEntry.getValue()[ReflectTools.SETTER_INDEX]);
                })
                .collect(Collectors.toList());
    }

    private <T, P> SerializableBiConsumer<T, P> getSetterFromMethod(Method method) {
        if(method != null)
            return (t, o) -> {
                try {
                    method.invoke(t, o);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("cannot access method "+method.getName(), e);
                }
            };
        else if(this.isUsingFakeSettersWhenNotPresent())
            return (t, p) -> {};
        else return null;
    }

    @SuppressWarnings("unchecked") // P is the type of field, so all well here
    private <T, P> SerializableFunction<T, P> getGetterFromMethod(Method method) {
        if(method != null)
            return t -> {
                try {
                    return (P) method.invoke(t);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException(e);
                }
            };
        else if(this.isUsingFakeGettersWhenNotPresent())
            return t -> null;
        else return null;
    }

    @SuppressWarnings("unchecked")
    private <T, P> Property<T, P> buildDefinition(Class<T> type, Field field, Class<?> fieldType, Method getter, Method setter) {
        return new Property<>(type, field.getName(), (Class<P>) fieldType,
                this.getSetterFromMethod(setter),
                this.getGetterFromMethod(getter),
                this.metadataProviders.stream().flatMap(provider -> provider.getMetadata(field.getName(), field, setter, getter).stream()).collect(Collectors.toList())
        );
    }

    /**
     * Adds given {@link MetadataProvider}s. They will be called in order of adding.
     * @param providers An array of {@link MetadataProvider}s to add.
     */
    public final void addMetadataProvider(MetadataProvider... providers) {
        this.metadataProviders.addAll(Arrays.stream(providers).filter(Objects::nonNull).collect(Collectors.toList()));
    }

    /**
     * Chains {@link #addMetadataProvider(MetadataProvider...)} and returns itself.
     * @param providers An array of {@link MetadataProvider}s to add.
     * @return This.
     * @see #addMetadataProvider(MetadataProvider...)
     */
    public final ReflectivePropertyProvider withMetadataProvider(MetadataProvider... providers) {
        this.addMetadataProvider(providers);
        return this;
    }

    /**
     * Checks whether a fake setter (i.e. one that does nothing) is returned when there is no setter for a property.
     * @return When {@code true}, a fake setter is used instead of a {@code null} value (default).
     */
    public boolean isUsingFakeSettersWhenNotPresent() {
        return usingFakeSettersWhenNotPresent;
    }

    /**
     * Allows using a fake setter when there is no setter for a given property. This allows setters to be called regardless of whether they exist or not.
     * @param usingFakeSettersWhenNotPresent When {@code true} and a setter for a property is missing, a fake one will be used instead of {@code null}.
     */
    public void setUsingFakeSettersWhenNotPresent(boolean usingFakeSettersWhenNotPresent) {
        this.usingFakeSettersWhenNotPresent = usingFakeSettersWhenNotPresent;
    }

    /**
     * Chains {@link #setUsingFakeGettersWhenNotPresent(boolean)} and returns itself.
     * @param setting Whether to fake setters.
     * @return This.
     * @see #setUsingFakeSettersWhenNotPresent(boolean)
     */
    public final ReflectivePropertyProvider withUsingFakeSettersWhenNotPresent(boolean setting) {
        this.setUsingFakeSettersWhenNotPresent(setting);
        return this;
    }

    /**
     * Checks whether a fake getter (i.e. one that always returns {@code null}) is returned when there is no getter for a property.
     * @return When {@code true}, a fake getter is used instead of a {@code null} value (default).
     */
    public boolean isUsingFakeGettersWhenNotPresent() {
        return usingFakeGettersWhenNotPresent;
    }

    /**
     * Allows using a fake getter when there is no getter for a given property. This allows getters to be called regardless of whether they exist or not.
     * @param usingFakeGettersWhenNotPresent When {@code true} and a getter for a property is missing, a fake one will be used instead of {@code null}.
     */
    public void setUsingFakeGettersWhenNotPresent(boolean usingFakeGettersWhenNotPresent) {
        this.usingFakeGettersWhenNotPresent = usingFakeGettersWhenNotPresent;
    }

    /**
     * Chains {@link #setUsingFakeGettersWhenNotPresent(boolean)} and returns itself.
     * @param setting When {@code true} and a getter for a property is missing, a fake one will be used instead of {@code null}.
     * @return This.
     * @see #setUsingFakeGettersWhenNotPresent(boolean)
     */
    public final ReflectivePropertyProvider withUsingFakeGettersWhenNotPresent(boolean setting) {
        this.setUsingFakeGettersWhenNotPresent(setting);
        return this;
    }
}
