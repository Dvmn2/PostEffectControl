package net.dvmn2.posteffectcontrol.client.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.dvmn2.posteffectcontrol.client.PostEffectControlConfig;
import net.dvmn2.posteffectcontrol.client.state.ActiveEffects;
import net.dvmn2.posteffectcontrol.client.sync.SyncManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

/**
 * Client command {@code /posteffectcontrol} (alias {@code /pec}): the player's emergency switch and settings.
 * <pre>
 * /posteffectcontrol status            show the current settings
 * /posteffectcontrol off               hide all effects immediately
 * /posteffectcontrol on                show effects again
 * /posteffectcontrol max &lt;0..1&gt;       ceiling for the intensity of any effect
 * /posteffectcontrol noflash &lt;bool&gt;   hide effects marked as flashing
 * /posteffectcontrol packeffects &lt;bool&gt;  allow effects from your resource packs (reported to the server)
 * </pre>
 * Subcommands unknown to the client (the server plugin's) are forwarded to the server by Fabric.
 */
public final class PostEffectControlCommand {

    private static final String ARG_VALUE = "value";

    private final SyncManager sync;

    public PostEffectControlCommand(SyncManager sync) {
        this.sync = sync;
    }

    /**
     * Registers the command and its {@code /pec} alias.
     */
    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralCommandNode<FabricClientCommandSource> node = dispatcher.register(build());
        dispatcher.register(ClientCommandManager.literal("pec").executes(this::status).redirect(node));
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> build() {
        return ClientCommandManager.literal("posteffectcontrol")
                .executes(this::status)
                .then(ClientCommandManager.literal("status").executes(this::status))
                .then(ClientCommandManager.literal("off").executes(ctx -> setEnabled(ctx, false)))
                .then(ClientCommandManager.literal("on").executes(ctx -> setEnabled(ctx, true)))
                .then(ClientCommandManager.literal("max")
                        .then(ClientCommandManager.argument(ARG_VALUE, FloatArgumentType.floatArg(0f, 1f))
                                .executes(this::setMax)))
                .then(ClientCommandManager.literal("noflash")
                        .then(ClientCommandManager.argument(ARG_VALUE, BoolArgumentType.bool())
                                .executes(this::setNoFlash)))
                .then(ClientCommandManager.literal("packeffects")
                        .then(ClientCommandManager.argument(ARG_VALUE, BoolArgumentType.bool())
                                .executes(this::setPackEffects)));
    }

    private int setEnabled(CommandContext<FabricClientCommandSource> ctx, boolean enabled) {
        PostEffectControlConfig config = PostEffectControlConfig.get();
        config.enabled = enabled;
        PostEffectControlConfig.save();
        sync.configChanged(ctx.getSource().getClient());
        ctx.getSource().sendFeedback(Text.translatable(
                enabled ? "posteffectcontrol.enabled" : "posteffectcontrol.disabled"));
        return Command.SINGLE_SUCCESS;
    }

    private int setMax(CommandContext<FabricClientCommandSource> ctx) {
        PostEffectControlConfig config = PostEffectControlConfig.get();
        config.maxIntensity = FloatArgumentType.getFloat(ctx, ARG_VALUE);
        PostEffectControlConfig.save();
        ctx.getSource().sendFeedback(Text.translatable("posteffectcontrol.max", String.valueOf(config.maxIntensity)));
        return Command.SINGLE_SUCCESS;
    }

    private int setNoFlash(CommandContext<FabricClientCommandSource> ctx) {
        PostEffectControlConfig config = PostEffectControlConfig.get();
        config.reduceFlashing = BoolArgumentType.getBool(ctx, ARG_VALUE);
        PostEffectControlConfig.save();
        ctx.getSource().sendFeedback(Text.translatable(
                config.reduceFlashing ? "posteffectcontrol.noflash.on" : "posteffectcontrol.noflash.off"));
        return Command.SINGLE_SUCCESS;
    }

    private int setPackEffects(CommandContext<FabricClientCommandSource> ctx) {
        PostEffectControlConfig config = PostEffectControlConfig.get();
        config.allowPackEffects = BoolArgumentType.getBool(ctx, ARG_VALUE);
        PostEffectControlConfig.save();
        sync.configChanged(ctx.getSource().getClient());
        ctx.getSource().sendFeedback(Text.translatable(
                config.allowPackEffects ? "posteffectcontrol.packeffects.on" : "posteffectcontrol.packeffects.off"));
        return Command.SINGLE_SUCCESS;
    }

    private int status(CommandContext<FabricClientCommandSource> ctx) {
        PostEffectControlConfig config = PostEffectControlConfig.get();
        String state = config.enabled ? "posteffectcontrol.state.enabled" : "posteffectcontrol.state.disabled";
        ctx.getSource().sendFeedback(Text.translatable("posteffectcontrol.status",
                Text.translatable(state),
                String.valueOf(config.maxIntensity),
                yesNo(config.reduceFlashing),
                yesNo(config.allowPackEffects),
                String.valueOf(ActiveEffects.getAssignedCount())));
        return Command.SINGLE_SUCCESS;
    }

    private static Text yesNo(boolean value) {
        return Text.translatable(value ? "posteffectcontrol.yes" : "posteffectcontrol.no");
    }
}
