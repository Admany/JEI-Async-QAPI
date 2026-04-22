package mezz.jei.library.recipes.collect;

import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IngredientToRecipesMap<R> {
	private final Map<Object, List<R>> uidToRecipes = new ConcurrentHashMap<>();

	public void add(R recipe, Collection<Object> ingredientUids) {
		for (Object uid : ingredientUids) {
			uidToRecipes.compute(uid, (k, recipes) -> {
				if (recipes == null) {
					recipes = Collections.synchronizedList(new ArrayList<>());
				}
				recipes.add(recipe);
				return recipes;
			});
		}
	}

	@UnmodifiableView
	public List<R> get(Object ingredientUid) {
		List<R> recipes = uidToRecipes.get(ingredientUid);
		if (recipes == null) {
			return Collections.emptyList();
		}
		synchronized (recipes) {
			return List.copyOf(recipes);
		}
	}

	public void compact() {
		for (Map.Entry<Object, List<R>> entry : uidToRecipes.entrySet()) {
			List<R> recipes = entry.getValue();
			synchronized (recipes) {
				if (recipes instanceof ArrayList<R> list) {
					list.trimToSize();
				}
			}
		}
	}
}
