package mezz.jei.library.plugins.vanilla.anvil;

import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.recipe.category.extensions.vanilla.smithing.ISmithingCategoryExtension;
import mezz.jei.common.platform.IPlatformRecipeHelper;
import mezz.jei.library.util.RecipeUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

public abstract class SmithingCategoryExtension<R extends SmithingRecipe> implements ISmithingCategoryExtension<R> {
	private static final Logger LOGGER = LogManager.getLogger();
	private final IPlatformRecipeHelper recipeHelper;

	public SmithingCategoryExtension(IPlatformRecipeHelper recipeHelper) {
		this.recipeHelper = recipeHelper;
	}

	@Override
	public <T extends IIngredientAcceptor<T>> void setTemplate(R recipe, T ingredientAcceptor) {
		Ingredient ingredient = recipeHelper.getTemplate(recipe);
		ingredientAcceptor.addIngredients(ingredient);
	}

	@Override
	public <T extends IIngredientAcceptor<T>> void setBase(R recipe, T ingredientAcceptor) {
		Ingredient ingredient = recipeHelper.getBase(recipe);
		ingredientAcceptor.addIngredients(ingredient);
	}

	@Override
	public <T extends IIngredientAcceptor<T>> void setAddition(R recipe, T ingredientAcceptor) {
		Ingredient ingredient = recipeHelper.getAddition(recipe);
		ingredientAcceptor.addIngredients(ingredient);
	}

	@Override
	public <T extends IIngredientAcceptor<T>> void setOutput(R recipe, T ingredientAcceptor) {
		Ingredient templateIngredient = recipeHelper.getTemplate(recipe);
		Ingredient baseIngredient = recipeHelper.getBase(recipe);
		Ingredient additionIngredient = recipeHelper.getAddition(recipe);

		List<ItemStack> templateStacks = Arrays.asList(templateIngredient.getItems());
		if (templateStacks.isEmpty()) {
			templateStacks = List.of(ItemStack.EMPTY);
		}

		List<ItemStack> baseStacks = Arrays.asList(baseIngredient.getItems());
		if (baseStacks.isEmpty()) {
			baseStacks = List.of(ItemStack.EMPTY);
		}

		ItemStack addition = ItemStack.EMPTY;
		ItemStack[] additions = additionIngredient.getItems();
		if (additions.length > 0) {
			addition = additions[0];
		}

		for (ItemStack template : templateStacks) {
			for (ItemStack base : baseStacks) {
				try {
					SmithingRecipeInput recipeInput = new SmithingRecipeInput(template, base, addition);
					ItemStack output = RecipeUtil.assembleResultItem(recipeInput, recipe);
					ingredientAcceptor.addItemStack(output);
				} catch (RuntimeException | LinkageError e) {
					LOGGER.debug("Failed to assemble smithing recipe output for recipe: {}", recipe, e);
				}
			}
		}
	}
}
