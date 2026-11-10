package com.kragnoth.cultivar.core.module;

import com.kragnoth.cultivar.compat.create.CreateModule;
import com.kragnoth.cultivar.content.core.CoreModule;
import com.kragnoth.cultivar.content.crop.CropModule;
import com.kragnoth.cultivar.content.cloche.ClocheModule;
import com.kragnoth.cultivar.content.tools.ToolsModule;
import com.kragnoth.cultivar.content.ledger.LedgerModule;
import com.kragnoth.cultivar.content.assay.AssayModule;
import com.kragnoth.cultivar.content.packtools.PackToolsModule;
import com.kragnoth.cultivar.content.irrigation.IrrigationModule;

import java.util.List;
import java.util.function.Supplier;

/**
 * The module index. Add new modules here, one per line, keeping {@code core} first.
 * Suppliers keep module classes from loading until their turn.
 */
public final class CultivarModules {
    public static final List<Supplier<CultivarModule>> ALL = List.of(
            CoreModule::new,
            CropModule::new,
            ClocheModule::new,
            ToolsModule::new,
            LedgerModule::new,
            AssayModule::new,
            IrrigationModule::new,
            PackToolsModule::new,
            CreateModule::new
    );

    private CultivarModules() {}
}
