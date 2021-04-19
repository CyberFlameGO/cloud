//
// MIT License
//
// Copyright (c) 2021 Alexander Söderberg & Contributors
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
package cloud.commandframework.sponge;

import cloud.commandframework.CommandTree;
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.StaticArgument;
import cloud.commandframework.arguments.compound.FlagArgument;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.arguments.standard.BooleanArgument;
import cloud.commandframework.arguments.standard.ByteArgument;
import cloud.commandframework.arguments.standard.DoubleArgument;
import cloud.commandframework.arguments.standard.FloatArgument;
import cloud.commandframework.arguments.standard.IntegerArgument;
import cloud.commandframework.arguments.standard.ShortArgument;
import cloud.commandframework.arguments.standard.StringArgument;
import cloud.commandframework.arguments.standard.StringArrayArgument;
import cloud.commandframework.exceptions.ArgumentParseException;
import cloud.commandframework.exceptions.CommandExecutionException;
import cloud.commandframework.exceptions.InvalidCommandSenderException;
import cloud.commandframework.exceptions.InvalidSyntaxException;
import cloud.commandframework.exceptions.NoPermissionException;
import cloud.commandframework.exceptions.NoSuchCommandException;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.util.ComponentMessageThrowable;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.ArgumentReader;
import org.spongepowered.api.command.registrar.tree.ClientCompletionKeys;
import org.spongepowered.api.command.registrar.tree.CommandTreeNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

final class CloudSpongeCommand<C> implements Command.Raw {

    private static final Component NULL = Component.text("null");
    private static final Component MESSAGE_INTERNAL_ERROR =
            text("An internal error occurred while attempting to perform this command.", RED);
    private static final Component MESSAGE_NO_PERMS =
            text("I'm sorry, but you do not have permission to perform this command. "
                    + "Please contact the server administrators if you believe that this is in error.", RED);
    private static final Component MESSAGE_UNKNOWN_COMMAND = text("Unknown command. Type \"/help\" for help.");

    private final SpongeCommandManager<C> commandManager;
    private final String label;

    CloudSpongeCommand(
            final @NonNull String label,
            final @NonNull SpongeCommandManager<C> commandManager
    ) {
        this.label = label;
        this.commandManager = commandManager;
    }

    @Override
    public CommandResult process(final @NonNull CommandCause cause, final ArgumentReader.@NonNull Mutable arguments) {
        final C cloudSender = this.commandManager.backwardsCauseMapper().apply(cause);
        final Audience audience = cause.audience();
        final String args = arguments.input();
        final String input;
        if (args.isEmpty()) {
            input = this.label;
        } else {
            input = this.label + " " + args;
        }
        this.commandManager.executeCommand(cloudSender, input).whenComplete((result, throwable) -> {
            if (throwable == null) {
                return;
            }
            if (throwable instanceof CompletionException) {
                throwable = throwable.getCause();
            }
            final Throwable finalThrowable = throwable;
            if (throwable instanceof InvalidSyntaxException) {
                this.commandManager.handleException(cloudSender,
                        InvalidSyntaxException.class,
                        (InvalidSyntaxException) throwable, (c, e) -> audience.sendMessage(TextComponent.ofChildren(
                                text("Invalid Command Syntax. Correct command syntax is: ", RED),
                                text("/" + ((InvalidSyntaxException) finalThrowable).getCorrectSyntax(), GRAY)
                        ))
                );
            } else if (throwable instanceof InvalidCommandSenderException) {
                this.commandManager.handleException(cloudSender,
                        InvalidCommandSenderException.class,
                        (InvalidCommandSenderException) throwable, (c, e) ->
                                audience.sendMessage(text(finalThrowable.getMessage(), RED))
                );
            } else if (throwable instanceof NoPermissionException) {
                this.commandManager.handleException(cloudSender,
                        NoPermissionException.class,
                        (NoPermissionException) throwable, (c, e) ->
                                audience.sendMessage(MESSAGE_NO_PERMS)
                );
            } else if (throwable instanceof NoSuchCommandException) {
                this.commandManager.handleException(cloudSender,
                        NoSuchCommandException.class,
                        (NoSuchCommandException) throwable, (c, e) ->
                                audience.sendMessage(MESSAGE_UNKNOWN_COMMAND)
                );
            } else if (throwable instanceof ArgumentParseException) {
                this.commandManager.handleException(cloudSender,
                        ArgumentParseException.class,
                        (ArgumentParseException) throwable, (c, e) ->
                                audience.sendMessage(TextComponent.ofChildren(
                                        text("Invalid Command Argument: ", RED),
                                        getMessage(finalThrowable.getCause()).colorIfAbsent(GRAY)
                                ))
                );
            } else if (throwable instanceof CommandExecutionException) {
                this.commandManager.handleException(cloudSender,
                        CommandExecutionException.class,
                        (CommandExecutionException) throwable, (c, e) -> {
                            audience.sendMessage(MESSAGE_INTERNAL_ERROR);
                            this.commandManager.getOwningPlugin().getLogger().error(
                                    "Exception executing command handler",
                                    finalThrowable.getCause()
                            );
                        }
                );
            } else {
                audience.sendMessage(MESSAGE_INTERNAL_ERROR);
                this.commandManager.getOwningPlugin().getLogger().error(
                        "An unhandled exception was thrown during command execution",
                        throwable
                );
            }
        });
        return CommandResult.success();
    }

    private static Component getMessage(final Throwable throwable) {
        final Component msg = ComponentMessageThrowable.getOrConvertMessage(throwable);
        return msg == null ? NULL : msg;
    }

    @Override
    public List<String> suggestions(final @NonNull CommandCause cause, final ArgumentReader.@NonNull Mutable arguments) {
        return this.commandManager.suggest(
                this.commandManager.backwardsCauseMapper().apply(cause),
                this.label + " " + arguments.input()
        );
    }

    @Override
    public boolean canExecute(final @NonNull CommandCause cause) {
        return true;
    }

    @Override
    public Optional<Component> shortDescription(final CommandCause cause) {
        return Optional.of(text("short desc!"));
    }

    @Override
    public Optional<Component> extendedDescription(final CommandCause cause) {
        return Optional.of(text("long desc!"));
    }

    @Override
    public Optional<Component> help(final @NonNull CommandCause cause) {
        return Raw.super.help(cause);
    }

    @Override
    public Component usage(final CommandCause cause) {
        return text("usage!");
    }

    @Override
    public CommandTreeNode.Root commandTree() {
        final CommandTreeNode<CommandTreeNode.Root> root = CommandTreeNode.root();

        final CommandTree.Node<CommandArgument<C, ?>> cloud = this.commandManager
                .getCommandTree()
                .getNamedNode(this.label);

        this.addChildren(root, cloud);

        return root.executable();
    }

    private void addChildren(final CommandTreeNode<?> node, final CommandTree.Node<CommandArgument<C, ?>> cloud) {
        for (final CommandTree.Node<CommandArgument<C, ?>> child : cloud.getChildren()) {
            final CommandArgument<C, ?> value = child.getValue();
            final CommandTreeNode<? extends CommandTreeNode.Argument<?>> treeNode;
            if (value instanceof StaticArgument) {
                treeNode = CommandTreeNode.literal();
            } else {
                treeNode = this.toSponge(value);
            }
            this.addChildren(treeNode, child);
            node.child(value.getName(), treeNode.executable());
        }
    }

    private CommandTreeNode<? extends CommandTreeNode.Argument<?>> toSponge(final CommandArgument<C, ?> value) {
        final ArgumentParser<C, ?> parser = value.getParser();
        final Function<Object, CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>>> mapper =
                MAPPERS.get(parser.getClass());
        if (mapper != null) {
            return mapper.apply(parser);
        }
        return ClientCompletionKeys.STRING.get().createNode().word();
    }

    private static final Map<Class<?>, Function<Object, CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>>>> MAPPERS
            = new HashMap<>();

    static {
        registerMapper(StringArgument.StringParser.class, stringParser -> {
            final StringArgument.StringMode mode = stringParser.getStringMode();
            if (mode == StringArgument.StringMode.SINGLE) {
                return ClientCompletionKeys.STRING.get().createNode().word();
            } else if (mode == StringArgument.StringMode.QUOTED) {
                return ClientCompletionKeys.STRING.get().createNode();
            } else if (mode == StringArgument.StringMode.GREEDY) {
                return ClientCompletionKeys.STRING.get().createNode().greedy();
            }
            throw new IllegalStateException("Unknown string mode '" + mode + "'!");
        });
        registerMapper(ByteArgument.ByteParser.class, byteParser -> {
            final CommandTreeNode.Range<Integer> node = ClientCompletionKeys.INTEGER.get().createNode();
            node.min((int) byteParser.getMin());
            node.max((int) byteParser.getMax());
            return node;
        });
        registerMapper(ShortArgument.ShortParser.class, shortParser -> {
            final CommandTreeNode.Range<Integer> node = ClientCompletionKeys.INTEGER.get().createNode();
            node.min((int) shortParser.getMin());
            node.max((int) shortParser.getMax());
            return node;
        });
        registerMapper(IntegerArgument.IntegerParser.class, integerParser -> {
            final CommandTreeNode.Range<Integer> node = ClientCompletionKeys.INTEGER.get().createNode();
            final boolean hasMin = integerParser.getMin() != Integer.MIN_VALUE;
            final boolean hasMax = integerParser.getMax() != Integer.MAX_VALUE;
            if (hasMin) {
                node.min(integerParser.getMin());
            }
            if (hasMax) {
                node.max(integerParser.getMax());
            }
            return node;
        });
        registerMapper(FloatArgument.FloatParser.class, floatParser -> {
            final CommandTreeNode.Range<Float> node = ClientCompletionKeys.FLOAT.get().createNode();
            final boolean hasMin = floatParser.getMin() != Float.NEGATIVE_INFINITY;
            final boolean hasMax = floatParser.getMax() != Float.POSITIVE_INFINITY;
            if (hasMin) {
                node.min(floatParser.getMin());
            }
            if (hasMax) {
                node.max(floatParser.getMax());
            }
            return node;
        });
        registerMapper(DoubleArgument.DoubleParser.class, doubleParser -> {
            final CommandTreeNode.Range<Double> node = ClientCompletionKeys.DOUBLE.get().createNode();
            final boolean hasMin = doubleParser.getMin() != Double.NEGATIVE_INFINITY;
            final boolean hasMax = doubleParser.getMax() != Double.POSITIVE_INFINITY;
            if (hasMin) {
                node.min(doubleParser.getMin());
            }
            if (hasMax) {
                node.max(doubleParser.getMax());
            }
            return node;
        });
        registerMapper(BooleanArgument.BooleanParser.class, booleanParser -> ClientCompletionKeys.BOOL.get().createNode());
        registerMapper(
                FlagArgument.FlagArgumentParser.class,
                flagArgumentParser -> ClientCompletionKeys.STRING.get().createNode().greedy()
        );
        registerMapper(
                StringArrayArgument.StringArrayParser.class,
                stringArrayParser -> ClientCompletionKeys.STRING.get().createNode().greedy()
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <A extends ArgumentParser<?, ?>> void registerMapper(
            final @NonNull Class<A> cloudType,
            final @NonNull Function<A, CommandTreeNode.Argument<?>> mapper
    ) {
        MAPPERS.put(cloudType, (Function) mapper);
    }

}
