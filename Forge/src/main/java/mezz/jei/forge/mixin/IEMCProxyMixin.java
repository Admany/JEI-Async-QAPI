package mezz.jei.forge.mixin;

import moze_intel.projecte.api.proxy.IEMCProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "moze_intel.projecte.api.proxy.IEMCProxy")
public interface IEMCProxyMixin {
	@Inject(
		method = "getValue",
		at = @At("HEAD"),
		cancellable = true
	)
	private void onGetValue(CallbackInfoReturnable<Long> cir) {
		if (IEMCProxy.INSTANCE == null) {
			cir.setReturnValue(0L);
		}
	}
}
