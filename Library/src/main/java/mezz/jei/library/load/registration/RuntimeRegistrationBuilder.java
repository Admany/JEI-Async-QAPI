package mezz.jei.library.load.registration;

import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.runtime.IIngredientManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal builder to bridge between the Library and Gui modules.
 * This class uses reflection to build GUI-specific data
 * from the Library module, avoiding direct compile-time dependencies.
 */
public class RuntimeRegistrationBuilder {
	private static final Logger LOGGER = LogManager.getLogger();

	/**
	 * Build the ingredient list using the Gui module's factory via reflection
	 * to avoid a direct dependency from Library to Gui.
	 */
	public static List<?> buildIngredientList(IIngredientManager ingredientManager, IModIdHelper modIdHelper) {
		try {
			Class<?> factoryClass = Class.forName("mezz.jei.gui.ingredients.IngredientListElementFactory");
			java.lang.reflect.Method createMethod = factoryClass.getMethod("createBaseList", IIngredientManager.class, IModIdHelper.class);
			return (List<?>) createMethod.invoke(null, ingredientManager, modIdHelper);
		} catch (Exception e) {
			LOGGER.error("Failed to pre-build ingredient list using Gui module factory", e);
			return new ArrayList<>();
		}
	}
}
