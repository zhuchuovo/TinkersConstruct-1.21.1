package net.minecraft.core.component;

import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public final class PatchedDataComponentMap implements DataComponentMap {
    private final DataComponentMap prototype;
    private Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch;
    private boolean copyOnWrite;

    public PatchedDataComponentMap(DataComponentMap prototype) {
        this(prototype, Reference2ObjectMaps.emptyMap(), true);
    }

    private PatchedDataComponentMap(DataComponentMap prototype, Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch, boolean copyOnWtite) {
        this.prototype = prototype;
        this.patch = patch;
        this.copyOnWrite = copyOnWtite;
    }

    public static PatchedDataComponentMap fromPatch(DataComponentMap prototype, DataComponentPatch patch) {
        if (isPatchSanitized(prototype, patch.map)) {
            return new PatchedDataComponentMap(prototype, patch.map, true);
        } else {
            PatchedDataComponentMap patcheddatacomponentmap = new PatchedDataComponentMap(prototype);
            patcheddatacomponentmap.applyPatch(patch);
            return patcheddatacomponentmap;
        }
    }

    private static boolean isPatchSanitized(DataComponentMap prototype, Reference2ObjectMap<DataComponentType<?>, Optional<?>> map) {
        for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(map)) {
            Object object = prototype.get(entry.getKey());
            Optional<?> optional = entry.getValue();
            if (optional.isPresent() && optional.get().equals(object)) {
                return false;
            }

            if (optional.isEmpty() && object == null) {
                return false;
            }
        }

        return true;
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> component) {
        Optional<? extends T> optional = (Optional<? extends T>)this.patch.get(component);
        return (T)(optional != null ? optional.orElse(null) : this.prototype.get(component));
    }

    @Nullable
    public <T> T set(DataComponentType<? super T> component, @Nullable T value) {
        net.neoforged.neoforge.common.CommonHooks.validateComponent(value);
        this.ensureMapOwnership();
        T t = this.prototype.get((DataComponentType<? extends T>)component);
        Optional<T> optional;
        if (Objects.equals(value, t)) {
            optional = (Optional<T>)this.patch.remove(component);
        } else {
            optional = (Optional<T>)this.patch.put(component, Optional.ofNullable(value));
        }

        return optional != null ? optional.orElse(t) : t;
    }

    @Nullable
    public <T> T remove(DataComponentType<? extends T> component) {
        this.ensureMapOwnership();
        T t = this.prototype.get(component);
        Optional<? extends T> optional;
        if (t != null) {
            optional = (Optional<? extends T>)this.patch.put(component, Optional.empty());
        } else {
            optional = (Optional<? extends T>)this.patch.remove(component);
        }

        return (T)(optional != null ? optional.orElse(null) : t);
    }

    public void applyPatch(DataComponentPatch patch) {
        this.ensureMapOwnership();

        for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(patch.map)) {
            this.applyPatch(entry.getKey(), entry.getValue());
        }
    }

    private void applyPatch(DataComponentType<?> component, Optional<?> value) {
        Object object = this.prototype.get(component);
        if (value.isPresent()) {
            if (value.get().equals(object)) {
                this.patch.remove(component);
            } else {
                this.patch.put(component, value);
            }
        } else if (object != null) {
            this.patch.put(component, Optional.empty());
        } else {
            this.patch.remove(component);
        }
    }

    public void restorePatch(DataComponentPatch patch) {
        this.ensureMapOwnership();
        this.patch.clear();
        this.patch.putAll(patch.map);
    }

    public void setAll(DataComponentMap map) {
        for (TypedDataComponent<?> typeddatacomponent : map) {
            typeddatacomponent.applyTo(this);
        }
    }

    private void ensureMapOwnership() {
        if (this.copyOnWrite) {
            this.patch = new Reference2ObjectArrayMap<>(this.patch);
            this.copyOnWrite = false;
        }
    }

    @Override
    public Set<DataComponentType<?>> keySet() {
        if (this.patch.isEmpty()) {
            return this.prototype.keySet();
        } else {
            Set<DataComponentType<?>> set = new ReferenceArraySet<>(this.prototype.keySet());

            for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(
                this.patch
            )) {
                Optional<?> optional = entry.getValue();
                if (optional.isPresent()) {
                    set.add(entry.getKey());
                } else {
                    set.remove(entry.getKey());
                }
            }

            return set;
        }
    }

    @Override
    public Iterator<TypedDataComponent<?>> iterator() {
        if (this.patch.isEmpty()) {
            return this.prototype.iterator();
        } else {
            List<TypedDataComponent<?>> list = new ArrayList<>(this.patch.size() + this.prototype.size());

            for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(
                this.patch
            )) {
                if (entry.getValue().isPresent()) {
                    list.add(TypedDataComponent.createUnchecked(entry.getKey(), entry.getValue().get()));
                }
            }

            for (TypedDataComponent<?> typeddatacomponent : this.prototype) {
                if (!this.patch.containsKey(typeddatacomponent.type())) {
                    list.add(typeddatacomponent);
                }
            }

            return list.iterator();
        }
    }

    @Override
    public int size() {
        int i = this.prototype.size();

        for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(this.patch)) {
            boolean flag = entry.getValue().isPresent();
            boolean flag1 = this.prototype.has(entry.getKey());
            if (flag != flag1) {
                i += flag ? 1 : -1;
            }
        }

        return i;
    }

    public boolean isPatchEmpty() {
        return this.patch.isEmpty();
    }

    public DataComponentPatch asPatch() {
        if (this.patch.isEmpty()) {
            return DataComponentPatch.EMPTY;
        } else {
            this.copyOnWrite = true;
            return new DataComponentPatch(this.patch);
        }
    }

    public PatchedDataComponentMap copy() {
        this.copyOnWrite = true;
        return new PatchedDataComponentMap(this.prototype, this.patch, true);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            if (other instanceof PatchedDataComponentMap patcheddatacomponentmap
                && this.prototype.equals(patcheddatacomponentmap.prototype)
                && this.patch.equals(patcheddatacomponentmap.patch)) {
                return true;
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.prototype.hashCode() + hashPatch(this.patch) * 31;
    }

    // Neo: Change implementation of hashCode to reduce collisions.
    // For a map, hashCode is specified as the sum of the hash codes of its entries.
    // We do that, but change the entry hash code to 31^<key hash> * <value hash>,
    // where <key hash> is the lower bits of the identity hash code of the key.
    private static int hashPatch(Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch) {
        int h = 0, n = patch.size();
        var iterator = it.unimi.dsi.fastutil.objects.Reference2ObjectMaps.fastIterator(patch);
        while (n-- != 0) {
            var entry = iterator.next();
            int exponent = System.identityHashCode(entry.getKey()) & 0xff;
            int entryHash = com.google.common.math.IntMath.pow(31, exponent) * entry.getValue().hashCode();
            h += entryHash;
        }
        return h;
    }

    @Override
    public String toString() {
        return "{" + this.stream().map(TypedDataComponent::toString).collect(Collectors.joining(", ")) + "}";
    }
}
