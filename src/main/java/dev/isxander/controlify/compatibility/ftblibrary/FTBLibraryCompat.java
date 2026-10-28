//? if ftb_library {
package dev.isxander.controlify.compatibility.ftblibrary;

import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import dev.isxander.controlify.screenop.ScreenProcessorProvider;

public final class FTBLibraryCompat {
    public static void init() {
        ScreenProcessorProvider.registerProvider(ScreenWrapper.class, FTBScreenProcessor::new);
    }
}
//?}
