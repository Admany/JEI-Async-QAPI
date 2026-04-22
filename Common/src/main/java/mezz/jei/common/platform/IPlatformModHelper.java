package mezz.jei.common.platform;

public interface IPlatformModHelper {
	String getModNameForModId(String modId);

	String getModVersionForModId(String modId);

	boolean isInDev();

	boolean isModLoaded(String modId);
}
