package com.mowmaster.pedestals.item.pedestalUpgrades;


import com.mojang.authlib.GameProfile;
import com.mowmaster.pedestals.network.PacketHandler;
import com.mowmaster.pedestals.network.PacketParticles;
import com.mowmaster.pedestals.tiles.PedestalTileEntity;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;

import static com.mowmaster.pedestals.pedestals.PEDESTALS_TAB;
import static com.mowmaster.pedestals.references.Reference.MODID;

public class ItemUpgradeMilker extends ItemUpgradeBaseFluid
{
    public int rangeHeight = 1;

    public ItemUpgradeMilker(Item.Properties builder) {super(builder.group(PEDESTALS_TAB));}

    @Override
    public Boolean canAcceptArea() {return true;}

    @Override
    public Boolean canAcceptRange() {
        return true;
    }

    @Override
    public Boolean canAcceptCapacity() {
        return true;
    }

    @Override
    public Boolean canAcceptAdvanced() {
        return true;
    }

    public int getAreaWidth(ItemStack stack)
    {
        int areaWidth = 0;
        int aW = getAreaModifier(stack);
        areaWidth = ((aW)+1);
        return  areaWidth;
    }

    public int getHeight(ItemStack stack)
    {
        return  getRangeTiny(stack);
    }

    public FluidStack getFluidInItem(ItemStack itemInPedestal)
    {
        //TODO: Maybe have a fluid recipe thingy for people to add other tiems that 'contain' fluids??? have an input, fluid, amount, and output for the recipe???
        if(FluidUtil.getFluidHandler(itemInPedestal).isPresent())
        {
            FluidStack fluidInItem = FluidUtil.getFluidContained(itemInPedestal).get();
            return fluidInItem;
        }
        return FluidStack.EMPTY;
    }

    @Override
    public int getWorkAreaX(World world, BlockPos pos, ItemStack coin)
    {
        return getAreaWidth(coin);
    }

    @Override
    public int[] getWorkAreaY(World world, BlockPos pos, ItemStack coin)
    {
        return new int[]{getHeight(coin),0};
    }

    @Override
    public int getWorkAreaZ(World world, BlockPos pos, ItemStack coin)
    {
        return getAreaWidth(coin);
    }

    public void updateAction(PedestalTileEntity pedestal)
    {
        World world = pedestal.getWorld();
        ItemStack coinInPedestal = pedestal.getCoinOnPedestal();
        ItemStack itemInPedestal = pedestal.getItemInPedestal();
        BlockPos pedestalPos = pedestal.getPos();
        if(!world.isRemote)
        {
            int getMaxFluidValue = getFluidbuffer(coinInPedestal);
            if(!hasMaxFluidSet(coinInPedestal) || readMaxFluidFromNBT(coinInPedestal) != getMaxFluidValue) {setMaxFluid(coinInPedestal, getMaxFluidValue);}

            int speed = getOperationSpeed(coinInPedestal);
            if(!world.isBlockPowered(pedestalPos))
            {
                if (world.getGameTime()%speed == 0) {
                    if(hasFluidInCoin(coinInPedestal))
                    {
                        upgradeActionSendFluid(pedestal);
                    }

                    upgradeAction(world, itemInPedestal, coinInPedestal, pedestalPos);
                }
            }
        }
    }

    public void upgradeAction(World world, ItemStack itemInPedestal, ItemStack coinInPedestal, BlockPos posOfPedestal)
    {
        int width = getAreaWidth(coinInPedestal);
        int height = getHeight(coinInPedestal);
        BlockPos negBlockPos = getNegRangePosEntity(world,posOfPedestal,width,height);
        BlockPos posBlockPos = getPosRangePosEntity(world,posOfPedestal,width,height);

        AxisAlignedBB getBox = new AxisAlignedBB(negBlockPos,posBlockPos);
        BlockPos posInventory = getPosOfBlockBelow(world,posOfPedestal,1);

        ItemStack itemFromInv = ItemStack.EMPTY;
        ItemStack itemForHand = new ItemStack(Items.BUCKET);
        ItemStack itemMilkBucket = new ItemStack(Items.MILK_BUCKET);


        FakePlayer fakePlayer = FakePlayerFactory.get((ServerWorld) world,new GameProfile(getPlayerFromCoin(coinInPedestal),"[Pedestals]"));
        fakePlayer.setPosition(posOfPedestal.getX(),posOfPedestal.getY(),posOfPedestal.getZ());

        LazyOptional<IItemHandler> cap = findItemHandlerAtPos(world,posInventory,getPedestalFacing(world, posOfPedestal),true);
        if(cap.isPresent())
        {
            IItemHandler handler = cap.orElse(null);
            TileEntity invToPullFrom = world.getTileEntity(posInventory);
            if(invToPullFrom instanceof PedestalTileEntity) {
                itemFromInv = ItemStack.EMPTY;

            }
            else {
                if(handler != null)
                {
                    int i = getNextSlotWithItemsCap(cap,getStackInPedestal(world,posOfPedestal));
                    if(i>=0)
                    {
                        itemFromInv = handler.getStackInSlot(i);
                        if(itemFromInv.getItem().equals(Items.BUCKET))
                        {
                            //Milking Code Here
                            ItemStack milkBucket = new ItemStack(Items.MILK_BUCKET,1);
                            List<CowEntity> moo = world.getEntitiesWithinAABB(CowEntity.class,getBox);
                            if(moo.size()>0)
                            {
                                for(CowEntity moomoo : moo)
                                {
                                    if (!moomoo.isChild() && itemInPedestal.equals(ItemStack.EMPTY))
                                    {
                                        BlockPos mooie = moomoo.getPosition();
                                        PacketHandler.sendToNearby(world,posOfPedestal,new PacketParticles(PacketParticles.EffectType.ANY_COLOR,mooie.getX(),mooie.getY()+0.5,mooie.getZ(),255,255,255));
                                        world.playSound((PlayerEntity) null, posOfPedestal.getX(), posOfPedestal.getY(), posOfPedestal.getZ(), SoundEvents.ENTITY_COW_MILK, SoundCategory.BLOCKS, 0.5F, 1.0F);
                                        TileEntity pedestalInv = world.getTileEntity(posOfPedestal);
                                        if(pedestalInv instanceof PedestalTileEntity) {
                                            handler.extractItem(i,1 ,false );
                                            ((PedestalTileEntity) pedestalInv).addItem(milkBucket);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        else
        {
            if(availableFluidSpaceInCoin(coinInPedestal)>= FluidAttributes.BUCKET_VOLUME)
            {
                FluidStack fluid = getFluidInItem(itemMilkBucket);
                if(!fluid.isEmpty())
                {
                    List<CowEntity> moo = world.getEntitiesWithinAABB(CowEntity.class,getBox);
                    if(moo.size()>0)
                    {
                        for(CowEntity moomoo : moo)
                        {
                            TileEntity pedestalInv = world.getTileEntity(posOfPedestal);
                            if(pedestalInv instanceof PedestalTileEntity) {
                                PedestalTileEntity pedestal = (PedestalTileEntity)pedestalInv;
                                if(!fakePlayer.getHeldItemMainhand().equals(itemInPedestal))
                                {
                                    fakePlayer.setHeldItem(Hand.MAIN_HAND,itemForHand);
                                }
                                ActionResultType result = moomoo.func_230254_b_(fakePlayer,Hand.MAIN_HAND);
                                fluid = getFluidInItem(fakePlayer.getHeldItemMainhand());
                                if (result.isSuccessOrConsume() && addFluid(pedestal,coinInPedestal,fluid,true))
                                {
                                    BlockPos mooie = moomoo.getPosition();
                                    PacketHandler.sendToNearby(world,posOfPedestal,new PacketParticles(PacketParticles.EffectType.ANY_COLOR,mooie.getX(),mooie.getY()+0.5,mooie.getZ(),255,255,255));
                                    world.playSound((PlayerEntity) null, posOfPedestal.getX(), posOfPedestal.getY(), posOfPedestal.getZ(), SoundEvents.ENTITY_COW_MILK, SoundCategory.BLOCKS, 0.5F, 1.0F);
                                    addFluid(pedestal,coinInPedestal,fluid,false);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    @Override
    public void chatDetails(PlayerEntity player, PedestalTileEntity pedestal)
    {
        ItemStack stack = pedestal.getCoinOnPedestal();

        TranslationTextComponent name = new TranslationTextComponent(getTranslationKey() + ".tooltip_name");
        name.mergeStyle(TextFormatting.GOLD);
        player.sendMessage(name,Util.DUMMY_UUID);

        int s3 = getAreaWidth(stack);
        String tr = "" + (s3+s3+1) + "";
        TranslationTextComponent area = new TranslationTextComponent(getTranslationKey() + ".chat_area");
        TranslationTextComponent areax = new TranslationTextComponent(getTranslationKey() + ".chat_areax");
        area.appendString(tr);
        area.appendString(areax.getString());
        area.appendString("" + getHeight(stack) + "");
        area.appendString(areax.getString());
        area.appendString(tr);
        area.mergeStyle(TextFormatting.WHITE);
        player.sendMessage(area,Util.DUMMY_UUID);

        FluidStack fluidStored = getFluidStored(stack);
        TranslationTextComponent fluidLabel = new TranslationTextComponent(getTranslationKey() + ".chat_fluidlabel");
        if(!fluidStored.isEmpty())
        {
            TranslationTextComponent fluid = new TranslationTextComponent(getTranslationKey() + ".chat_fluid");
            TranslationTextComponent fluidSplit = new TranslationTextComponent(getTranslationKey() + ".chat_fluidseperator");
            fluid.appendString("" + fluidStored.getDisplayName().getString() + "");
            fluid.appendString(fluidSplit.getString());
            fluid.appendString("" + fluidStored.getAmount() + "");
            fluid.appendString(fluidLabel.getString());
            fluid.mergeStyle(TextFormatting.BLUE);
            player.sendMessage(fluid,Util.DUMMY_UUID);
        }

        TranslationTextComponent rate = new TranslationTextComponent(getTranslationKey() + ".chat_rate");
        rate.appendString("" +  getFluidTransferRate(stack) + "");
        rate.appendString(fluidLabel.getString());
        rate.mergeStyle(TextFormatting.GRAY);
        player.sendMessage(rate,Util.DUMMY_UUID);

        //Display Speed Last Like on Tooltips
        TranslationTextComponent speed = new TranslationTextComponent(getTranslationKey() + ".chat_speed");
        speed.appendString(getOperationSpeedString(stack));
        speed.mergeStyle(TextFormatting.RED);
        player.sendMessage(speed, Util.DUMMY_UUID);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn) {
        TranslationTextComponent t = new TranslationTextComponent(getTranslationKey() + ".tooltip_name");
        t.mergeStyle(TextFormatting.GOLD);
        tooltip.add(t);

        int s3 = getAreaWidth(stack);
        String tr = "" + (s3+s3+1) + "";
        TranslationTextComponent area = new TranslationTextComponent(getTranslationKey() + ".tooltip_area");
        TranslationTextComponent areax = new TranslationTextComponent(getTranslationKey() + ".tooltip_areax");
        area.appendString(tr);
        area.appendString(areax.getString());
        area.appendString("" + getHeight(stack) + "");
        area.appendString(areax.getString());
        area.appendString(tr);
        area.mergeStyle(TextFormatting.WHITE);
        tooltip.add(area);

        FluidStack fluidStored = getFluidStored(stack);
        TranslationTextComponent fluidLabel = new TranslationTextComponent(getTranslationKey() + ".chat_fluidlabel");
        if(!fluidStored.isEmpty())
        {
            TranslationTextComponent fluid = new TranslationTextComponent(getTranslationKey() + ".chat_fluid");
            TranslationTextComponent fluidSplit = new TranslationTextComponent(getTranslationKey() + ".chat_fluidseperator");
            fluid.appendString("" + fluidStored.getDisplayName().getString() + "");
            fluid.appendString(fluidSplit.getString());
            fluid.appendString("" + fluidStored.getAmount() + "");
            fluid.appendString(fluidLabel.getString());
            fluid.mergeStyle(TextFormatting.BLUE);
            tooltip.add(fluid);
        }

        TranslationTextComponent fluidcapacity = new TranslationTextComponent(getTranslationKey() + ".tooltip_fluidcapacity");
        fluidcapacity.appendString(""+ getFluidbuffer(stack) +"");
        fluidcapacity.appendString(fluidLabel.getString());
        fluidcapacity.mergeStyle(TextFormatting.AQUA);
        tooltip.add(fluidcapacity);

        TranslationTextComponent rate = new TranslationTextComponent(getTranslationKey() + ".tooltip_rate");
        rate.appendString("" + getFluidTransferRate(stack) + "");
        rate.appendString(fluidLabel.getString());
        rate.mergeStyle(TextFormatting.GRAY);
        tooltip.add(rate);

        TranslationTextComponent speed = new TranslationTextComponent(getTranslationKey() + ".tooltip_speed");
        speed.appendString(getOperationSpeedString(stack));
        speed.mergeStyle(TextFormatting.RED);
        tooltip.add(speed);
    }

    public static final Item MILKER = new ItemUpgradeMilker(new Item.Properties().maxStackSize(64).group(PEDESTALS_TAB)).setRegistryName(new ResourceLocation(MODID, "coin/milker"));

    @SubscribeEvent
    public static void onItemRegistryReady(RegistryEvent.Register<Item> event)
    {
        event.getRegistry().register(MILKER);
    }


}
