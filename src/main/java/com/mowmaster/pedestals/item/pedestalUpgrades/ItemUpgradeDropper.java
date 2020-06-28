package com.mowmaster.pedestals.item.pedestalUpgrades;

import com.mowmaster.pedestals.pedestals;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.List;

import static com.mowmaster.pedestals.pedestals.PEDESTALS_TAB;
import static com.mowmaster.pedestals.references.Reference.MODID;

public class ItemUpgradeDropper extends ItemUpgradeBase
{
    public ItemUpgradeDropper(Item.Properties builder) {super(builder.group(PEDESTALS_TAB));}

    @Override
    public Boolean canAcceptRange() {
        return true;
    }

    @Override
    public Boolean canAcceptCapacity() {
        return true;
    }

    public void updateAction(int tick, World world, ItemStack itemInPedestal, ItemStack coinInPedestal, BlockPos pedestalPos)
    {
        int speed = getOperationSpeed(coinInPedestal);
        if(!world.isBlockPowered(pedestalPos))
        {
            if (tick%speed == 0) {
                upgradeAction(world,pedestalPos,coinInPedestal);
            }
        }
    }

    public void upgradeAction(World world, BlockPos posOfPedestal, ItemStack coinOnPedestal)
    {
        int rate = getItemTransferRate(coinOnPedestal);
        int range = getRange(coinOnPedestal);
        if(!getStackInPedestal(world,posOfPedestal).isEmpty())//hasItem
        {
            ItemStack itemToSummon = getStackInPedestal(world,posOfPedestal).copy();
            itemToSummon.setCount(rate);
            ItemEntity itemEntity = new ItemEntity(world,getPosOfBlockBelow(world,posOfPedestal,-range).getX() + 0.5,getPosOfBlockBelow(world,posOfPedestal,-range).getY(),getPosOfBlockBelow(world,posOfPedestal,-range).getZ() + 0.5,itemToSummon);
            itemEntity.setMotion(0,0,0);
            world.addEntity(itemEntity);
            this.removeFromPedestal(world,posOfPedestal,itemToSummon.getCount());
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);

        TranslationTextComponent rate = new TranslationTextComponent(getTranslationKey() + ".tooltip_rate");
        rate.func_240702_b_("" + getItemTransferRate(stack) + "");
        TranslationTextComponent range = new TranslationTextComponent(getTranslationKey() + ".tooltip_range");
        range.func_240702_b_("" + getRange(stack) + "");
        TranslationTextComponent speed = new TranslationTextComponent(getTranslationKey() + ".tooltip_speed");
        speed.func_240702_b_(getOperationSpeedString(stack));

        rate.func_240699_a_(TextFormatting.GRAY);
        range.func_240699_a_(TextFormatting.WHITE);
        speed.func_240699_a_(TextFormatting.RED);

        tooltip.add(rate);
        tooltip.add(range);
        tooltip.add(speed);
    }

    public static final Item DROPPER = new ItemUpgradeDropper(new Item.Properties().maxStackSize(64).group(PEDESTALS_TAB)).setRegistryName(new ResourceLocation(MODID, "coin/dropper"));

    @SubscribeEvent
    public static void onItemRegistryReady(RegistryEvent.Register<Item> event)
    {
        event.getRegistry().register(DROPPER);
    }


}