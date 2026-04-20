package mezz.jei.library.ingredients;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class TypedIngredientSet<T> extends AbstractSet<ITypedIngredient<T>> {
	private static final Logger LOGGER = LogManager.getLogger();

	private final IIngredientHelper<T> ingredientHelper;
	private final UidContext context;
	private final Map<Object, ITypedIngredient<T>> ingredients;

	public TypedIngredientSet(IIngredientHelper<T> ingredientHelper, UidContext context) {
		this.ingredientHelper = ingredientHelper;
		this.context = context;
		this.ingredients = new LinkedHashMap<>();
	}

	@Nullable
	private Object getUid(ITypedIngredient<T> typedIngredient) {
		try {
			return ingredientHelper.getUid(typedIngredient, context);
		} catch (RuntimeException e) {
			try {
				String ingredientInfo = ingredientHelper.getErrorInfo(typedIngredient.getIngredient());
				LOGGER.warn("Found a broken ingredient {}", ingredientInfo, e);
			} catch (RuntimeException e2) {
				LOGGER.warn("Found a broken ingredient.", e2);
			}
			return null;
		}
	}

	@Override
	public synchronized boolean add(ITypedIngredient<T> value) {
		Object uid = getUid(value);
		return uid != null && ingredients.put(uid, value) == null;
	}

	@Override
	public synchronized boolean remove(Object value) {
		if (value instanceof ITypedIngredient<?> typedIngredient) {
			IIngredientType<?> type = typedIngredient.getType();
			if (this.ingredientHelper.getIngredientType().equals(type)) {
				@SuppressWarnings("unchecked")
				ITypedIngredient<T> cast = (ITypedIngredient<T>) typedIngredient;
				Object uid = getUid(cast);
				return uid != null && ingredients.remove(uid) != null;
			}
		}
		return false;
	}

	@Override
	public synchronized boolean removeAll(Collection<?> c) {
		Objects.requireNonNull(c);
		boolean modified = false;
		for (Object value : c) {
			modified |= remove(value);
		}
		return modified;
	}

	@Override
	public synchronized boolean addAll(Collection<? extends ITypedIngredient<T>> c) {
		Objects.requireNonNull(c);
		boolean modified = false;
		for (ITypedIngredient<T> value : c) {
			modified |= add(value);
		}
		return modified;
	}

	@Override
	public synchronized boolean contains(Object value) {
		if (value instanceof ITypedIngredient<?> typedIngredient) {
			IIngredientType<?> type = typedIngredient.getType();
			if (this.ingredientHelper.getIngredientType().equals(type)) {
				@SuppressWarnings("unchecked")
				ITypedIngredient<T> cast = (ITypedIngredient<T>) typedIngredient;
				Object uid = getUid(cast);
				return uid != null && ingredients.containsKey(uid);
			}
		}
		return false;
	}

	@SuppressWarnings("removal")
	@Deprecated(forRemoval = true)
	public synchronized Optional<ITypedIngredient<T>> getByLegacyUid(String uid) {
		ITypedIngredient<T> v = ingredients.get(uid);
		if (v != null) {
			return Optional.of(v);
		}

		for (ITypedIngredient<T> typedIngredient : ingredients.values()) {
			String legacyUid = ingredientHelper.getUniqueId(typedIngredient.getIngredient(), context);
			if (uid.equals(legacyUid)) {
				return Optional.of(typedIngredient);
			}
		}
		return Optional.empty();
	}

	@Override
	public synchronized void clear() {
		ingredients.clear();
	}

	@Override
	public synchronized Iterator<ITypedIngredient<T>> iterator() {
		return new ArrayList<>(ingredients.values()).iterator();
	}

	@Override
	public synchronized int size() {
		return ingredients.size();
	}

	@Override
	public synchronized Object[] toArray() {
		return ingredients.values().toArray();
	}

	@Override
	public synchronized <T1> T1[] toArray(T1[] a) {
		return ingredients.values().toArray(a);
	}
}
