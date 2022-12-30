package com.smashingmods.chemlib.api.addons.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.smashingmods.chemlib.ChemLib;
import com.smashingmods.chemlib.api.addons.datagen.ModBlockStateGenerator;
import com.smashingmods.chemlib.api.addons.datagen.ModItemModelGenerator;
import com.smashingmods.chemlib.api.addons.datagen.ModItemTagGenerator;
import com.smashingmods.chemlib.api.addons.datagen.ModLocalizationGenerator;
import com.smashingmods.chemlib.api.utility.FluidEffectsTooltipUtility;
import com.smashingmods.chemlib.common.items.ChemicalItem;
import com.smashingmods.chemlib.common.items.CompoundItem;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.data.DataGenerator;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.model.DynamicFluidContainerModel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AddonRegisters {
    public static String modID;
    private CreativeModeTab bucketsTab = null;
    private CreativeModeTab compoundsTab = null;
    public DeferredRegister<Item> COMPOUNDS;
    public DeferredRegister<Item> COMPOUND_DUSTS;
    public DeferredRegister<Fluid> FLUIDS;
    public DeferredRegister<FluidType> FLUID_TYPES;
    public DeferredRegister<Block> LIQUID_BLOCKS;
    public DeferredRegister<Item> BUCKETS;

    /**
     * Creates the required DeferredRegisters for you
     *
     * @param pModID Your Mod ID string
     */
    public AddonRegisters(String pModID) throws RuntimeException {
        modID = pModID;
        COMPOUNDS = DeferredRegister.create(ForgeRegistries.ITEMS, pModID);
        COMPOUND_DUSTS = DeferredRegister.create(ForgeRegistries.ITEMS, pModID);
        FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, pModID);
        FLUID_TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, pModID);
        LIQUID_BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, pModID);
        BUCKETS = DeferredRegister.create(ForgeRegistries.ITEMS, pModID);
        addListeners();
        if (!ModTracker.addModRegisters(this)) {
            throw new RuntimeException("Mod ID already used to create and register chemical compounds");
        }
    }

    /**
     * Pass in your own DeferredRegister objects
     *
     * @param pModID         Your Mod ID string
     * @param pCompounds     DeferredRegister for compounds
     * @param pCompoundDusts DeferredRegister for Compound dusts
     * @param pFluids        DeferredRegister for fluids
     * @param pFluidTypes    DeferredRegister for fluid types
     * @param pLiquidBlocks  DeferredRegister for liquid blocks
     * @param pBuckets       DeferredRegister for buckets
     */
    public AddonRegisters(String pModID, DeferredRegister<Item> pCompounds, DeferredRegister<Item> pCompoundDusts, DeferredRegister<Fluid> pFluids, DeferredRegister<FluidType> pFluidTypes, DeferredRegister<Block> pLiquidBlocks, DeferredRegister<Item> pBuckets) throws RuntimeException {
        modID = pModID;
        COMPOUNDS = pCompounds;
        COMPOUND_DUSTS = pCompoundDusts;
        FLUIDS = pFluids;
        FLUID_TYPES = pFluidTypes;
        LIQUID_BLOCKS = pLiquidBlocks;
        BUCKETS = pBuckets;
        addListeners();
        if (!ModTracker.addModRegisters(this)) {
            throw new RuntimeException("Mod ID already used to create and register chemical compounds");
        }
    }

    private void addListeners() {
        IEventBus iEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        iEventBus.addListener(this::gatherData);
        iEventBus.addListener(this::onClientSetupEvent);
        iEventBus.addListener(this::onItemColorHandlerEvent);
        MinecraftForge.EVENT_BUS.addListener(this::onRenderTooltip);
    }

    public static JsonObject getStreamAsJsonObject(Class<?> pCaller, String pPath) {
        return JsonParser.parseReader(new BufferedReader(new InputStreamReader(Objects.requireNonNull(pCaller.getResourceAsStream(pPath))))).getAsJsonObject();
    }

    public void registerCompounds(IEventBus pEventBus, JsonObject pCompoundsJson) {
        CompoundRegistration.RegisterCompounds(this, pCompoundsJson);
        register(pEventBus);
    }

    public void registerCompounds(IEventBus pEventBus, Class<?> pCaller, String pPath) {
        JsonObject jsonObject = JsonParser.parseReader(new BufferedReader(new InputStreamReader(Objects.requireNonNull(pCaller.getResourceAsStream(pPath))))).getAsJsonObject();
        CompoundRegistration.RegisterCompounds(this, jsonObject);
        register(pEventBus);
    }

    private void register(IEventBus eventBus) {
        COMPOUNDS.register(eventBus);
        COMPOUND_DUSTS.register(eventBus);
        FLUIDS.register(eventBus);
        FLUID_TYPES.register(eventBus);
        LIQUID_BLOCKS.register(eventBus);
        BUCKETS.register(eventBus);
    }

    //region events
    public void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        generator.addProvider(event.includeClient(), new ModBlockStateGenerator(generator, this.getModID(), this.LIQUID_BLOCKS, event.getExistingFileHelper()));
        generator.addProvider(event.includeServer(), new ModItemModelGenerator(generator, this, event.getExistingFileHelper()));
        generator.addProvider(event.includeClient(), new ModLocalizationGenerator(generator, this, "en_us"));
        generator.addProvider(event.includeServer(), new ModItemTagGenerator(generator, this, event.getExistingFileHelper()));
    }

    public void onClientSetupEvent(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            getFluidsAsStream().forEach(fluid -> ItemBlockRenderTypes.setRenderLayer(fluid, RenderType.solid()));
            getLiquidBlocksAsStream().forEach(liquidBlock -> ItemBlockRenderTypes.setRenderLayer(liquidBlock, RenderType.solid()));
        });
    }

    public void onItemColorHandlerEvent(final RegisterColorHandlersEvent.Item event) {
        getCompounds().forEach(compound -> event.register(compound::getColor, compound));
        getCompoundItemsAsStream().forEach(item -> event.register(item::getColor, item));
        getBucketsAsStream().forEach(bucket -> event.register(new DynamicFluidContainerModel.Colors(), bucket));
    }

    public void onRenderTooltip(RenderTooltipEvent.GatherComponents event) {
        if (event.getItemStack().getItem() instanceof BucketItem bucket
                && ForgeRegistries.FLUIDS.getResourceKey(bucket.getFluid()).isPresent()
                && ForgeRegistries.FLUIDS.getResourceKey(bucket.getFluid()).get().location().getNamespace().equals(getModID())) {

            Function<FormattedText, Either<FormattedText, TooltipComponent>> formattedTextFunction = Either::left;

            for (FormattedText textElement : FluidEffectsTooltipUtility.getBucketEffectTooltipComponents(event.getItemStack())) {
                event.getTooltipElements().add(formattedTextFunction.apply(textElement));
            }
            String namespace = ForgeRegistries.FLUIDS.getResourceKey(bucket.getFluid()).get().location().getNamespace();
            event.getTooltipElements().add(formattedTextFunction.apply(MutableComponent.create(new LiteralContents(StringUtils.capitalize(namespace))).withStyle(ChemLib.MOD_ID_TEXT_STYLE)));
        }
    }
    //endregion

    //region Utilities
    public String getModID() {
        return modID;
    }

    /**
     * @param pCompoundsTab Custom creative tab for your compounds
     */
    public void setCompoundsTab(CreativeModeTab pCompoundsTab) {
        this.compoundsTab = pCompoundsTab;
    }

    /**
     * @param pBucketsTab Custom creative tab for your fluids
     */
    public void setBucketsTab(CreativeModeTab pBucketsTab) {
        this.bucketsTab = pBucketsTab;
    }

    public CreativeModeTab getBucketsTab() {
        return bucketsTab;
    }

    public CreativeModeTab getCompoundsTab() {
        return compoundsTab;
    }

    public List<CompoundItem> getCompounds() {
        return COMPOUNDS.getEntries().stream().map(RegistryObject::get).map(item -> (CompoundItem) item).collect(Collectors.toList());
    }

    public List<CompoundItem> getSortedCompounds() {
        List<CompoundItem> compounds = new ArrayList<>(COMPOUNDS.getEntries().stream().map(RegistryObject::get).map(item -> (CompoundItem) item).toList());
        compounds.sort(Comparator.comparing(CompoundItem::getChemicalName));
        return compounds;
    }

    public Optional<CompoundItem> getCompoundByName(String pName) {
        return getCompounds().stream().filter(compound -> compound.getChemicalName().equals(pName)).findFirst();
    }

    public List<ChemicalItem> getSortedChemicalItems() {
        return getCompoundItemsAsStream().sorted(Comparator.comparing(ChemicalItem::getChemicalName)).collect(Collectors.toList());
    }

    public Stream<ChemicalItem> getCompoundItemsAsStream() {
        return COMPOUND_DUSTS.getEntries().stream().map(RegistryObject::get).map(item -> (ChemicalItem) item);
    }

    public Stream<Fluid> getFluidsAsStream() {
        return FLUIDS.getEntries().stream().map(RegistryObject::get);
    }

    public Stream<ForgeFlowingFluid.Source> getSourceFluidsAsStream() {
        return getFluidsAsStream().filter(fluid -> fluid instanceof ForgeFlowingFluid.Source).map(fluid -> (ForgeFlowingFluid.Source) fluid);
    }

    public Stream<LiquidBlock> getLiquidBlocksAsStream() {
        return LIQUID_BLOCKS.getEntries().stream().map(RegistryObject::get).map(block -> (LiquidBlock) block);
    }

    public Stream<BucketItem> getBucketsAsStream() {
        return BUCKETS.getEntries().stream().map(RegistryObject::get).map(item -> (BucketItem) item);
    }

    public List<BucketItem> getSortedBuckets() {
        List<BucketItem> buckets = new java.util.ArrayList<>(getBucketsAsStream().toList());
        buckets.sort((BucketItem b1, BucketItem b2) -> b1.getFluid().getFluidType().toString().compareToIgnoreCase(b2.getFluid().getFluidType().toString()));
        return buckets;
    }
    //endregion
}