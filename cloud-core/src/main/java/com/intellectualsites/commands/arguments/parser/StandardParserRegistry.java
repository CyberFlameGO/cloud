//
// MIT License
//
// Copyright (c) 2020 Alexander Söderberg
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
package com.intellectualsites.commands.arguments.parser;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.intellectualsites.commands.annotations.specifier.Completions;
import com.intellectualsites.commands.annotations.specifier.Range;
import com.intellectualsites.commands.arguments.standard.BooleanArgument;
import com.intellectualsites.commands.arguments.standard.ByteArgument;
import com.intellectualsites.commands.arguments.standard.CharArgument;
import com.intellectualsites.commands.arguments.standard.DoubleArgument;
import com.intellectualsites.commands.arguments.standard.EnumArgument;
import com.intellectualsites.commands.arguments.standard.FloatArgument;
import com.intellectualsites.commands.arguments.standard.IntegerArgument;
import com.intellectualsites.commands.arguments.standard.ShortArgument;
import com.intellectualsites.commands.arguments.standard.StringArgument;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Standard implementation of {@link ParserRegistry}
 *
 * @param <C> Command sender type
 */
public final class StandardParserRegistry<C> implements ParserRegistry<C> {

    private static final Map<Class<?>, Class<?>> PRIMITIVE_MAPPINGS = ImmutableMap.<Class<?>, Class<?>>builder()
            .put(char.class, Character.class)
            .put(int.class, Integer.class)
            .put(short.class, Short.class)
            .put(byte.class, Byte.class)
            .put(float.class, Float.class)
            .put(double.class, Double.class)
            .put(long.class, Long.class)
            .put(boolean.class, Boolean.class)
            .build();

    private final Map<TypeToken<?>, Function<ParserParameters, ArgumentParser<C, ?>>> parserSuppliers = new HashMap<>();
    private final Map<Class<? extends Annotation>, BiFunction<? extends Annotation, TypeToken<?>, ParserParameters>>
            annotationMappers = new HashMap<>();

    /**
     * Construct a new {@link StandardParserRegistry} instance. This will also
     * register all standard annotation mappers and parser suppliers
     */
    public StandardParserRegistry() {
        /* Register standard mappers */
        this.<Range, Number>registerAnnotationMapper(Range.class, new RangeMapper<>());
        this.<Completions, String>registerAnnotationMapper(Completions.class, new CompletionsMapper());

        /* Register standard types */
        this.registerParserSupplier(TypeToken.of(Byte.class), options ->
                new ByteArgument.ByteParser<C>((byte) options.get(StandardParameters.RANGE_MIN, Byte.MIN_VALUE),
                                               (byte) options.get(StandardParameters.RANGE_MAX, Byte.MAX_VALUE)));
        this.registerParserSupplier(TypeToken.of(Short.class), options ->
                new ShortArgument.ShortParser<C>((short) options.get(StandardParameters.RANGE_MIN, Short.MIN_VALUE),
                                                 (short) options.get(StandardParameters.RANGE_MAX, Short.MAX_VALUE)));
        this.registerParserSupplier(TypeToken.of(Integer.class), options ->
                new IntegerArgument.IntegerParser<C>((int) options.get(StandardParameters.RANGE_MIN, Integer.MIN_VALUE),
                                                     (int) options.get(StandardParameters.RANGE_MAX, Integer.MAX_VALUE)));
        this.registerParserSupplier(TypeToken.of(Float.class), options ->
                new FloatArgument.FloatParser<C>((float) options.get(StandardParameters.RANGE_MIN, Float.MIN_VALUE),
                                                 (float) options.get(StandardParameters.RANGE_MAX, Float.MAX_VALUE)));
        this.registerParserSupplier(TypeToken.of(Double.class), options ->
                new DoubleArgument.DoubleParser<C>((double) options.get(StandardParameters.RANGE_MIN, Double.MIN_VALUE),
                                                   (double) options.get(StandardParameters.RANGE_MAX, Double.MAX_VALUE)));
        this.registerParserSupplier(TypeToken.of(Character.class), options -> new CharArgument.CharacterParser<C>());
        /* Make this one less awful */
        this.registerParserSupplier(TypeToken.of(String.class), options -> new StringArgument.StringParser<C>(
                StringArgument.StringMode.SINGLE, (context, s) ->
                Arrays.asList(options.get(StandardParameters.COMPLETIONS, new String[0]))));
        /* Add options to this */
        this.registerParserSupplier(TypeToken.of(Boolean.class), options -> new BooleanArgument.BooleanParser<>(false));
    }

    @Override
    public <T> void registerParserSupplier(@Nonnull final TypeToken<T> type,
                                           @Nonnull final Function<ParserParameters, ArgumentParser<C, ?>> supplier) {
        this.parserSuppliers.put(type, supplier);
    }

    @Override
    public <A extends Annotation, T> void registerAnnotationMapper(@Nonnull final Class<A> annotation,
                                                                   @Nonnull final BiFunction<A, TypeToken<?>,
                                                                           ParserParameters> mapper) {
        this.annotationMappers.put(annotation, mapper);
    }

    @Nonnull
    @Override
    public ParserParameters parseAnnotations(@Nonnull final TypeToken<?> parsingType,
                                             @Nonnull final Collection<? extends Annotation> annotations) {
        final ParserParameters parserParameters = new ParserParameters();
        annotations.forEach(annotation -> {
            // noinspection all
            final BiFunction mapper = this.annotationMappers.get(annotation.annotationType());
            if (mapper == null) {
                return;
            }
            @SuppressWarnings("unchecked") final ParserParameters parserParametersCasted = (ParserParameters) mapper.apply(
                    annotation, parsingType);
            parserParameters.merge(parserParametersCasted);
        });
        return parserParameters;
    }

    @Nonnull
    @Override
    public <T> Optional<ArgumentParser<C, T>> createParser(@Nonnull final TypeToken<T> type,
                                                           @Nonnull final ParserParameters parserParameters) {
        final TypeToken<?> actualType;
        if (type.isPrimitive()) {
            actualType = TypeToken.of(PRIMITIVE_MAPPINGS.get(type.getRawType()));
        } else {
            actualType = type;
        }
        final Function<ParserParameters, ArgumentParser<C, ?>> producer = this.parserSuppliers.get(actualType);
        if (producer == null) {
            /* Give enums special treatment */
            if (actualType.isSubtypeOf(Enum.class)) {
                @SuppressWarnings("all")
                final EnumArgument.EnumParser enumArgument = new EnumArgument.EnumParser((Class<Enum>)
                                                                                            actualType.getRawType());
                // noinspection all
                return Optional.of(enumArgument);
            }
            return Optional.empty();
        }
        @SuppressWarnings("unchecked") final ArgumentParser<C, T> parser = (ArgumentParser<C, T>) producer.apply(
                parserParameters);
        return Optional.of(parser);
    }


    private static final class RangeMapper<T> implements BiFunction<Range, TypeToken<?>, ParserParameters> {

        @Override
        public ParserParameters apply(final Range range, final TypeToken<?> type) {
            final Class<?> clazz;
            if (type.isPrimitive()) {
                clazz = PRIMITIVE_MAPPINGS.get(type.getRawType());
            } else {
                clazz = type.getRawType();
            }
            if (!Number.class.isAssignableFrom(clazz)) {
                return ParserParameters.empty();
            }
            Number min = null;
            Number max = null;
            if (clazz.equals(Byte.class)) {
                if (!range.min().isEmpty()) {
                    min = Byte.parseByte(range.min());
                }
                if (!range.max().isEmpty()) {
                    max = Byte.parseByte(range.max());
                }
            } else if (clazz.equals(Short.class)) {
                if (!range.min().isEmpty()) {
                    min = Short.parseShort(range.min());
                }
                if (!range.max().isEmpty()) {
                    max = Short.parseShort(range.max());
                }
            } else if (clazz.equals(Integer.class)) {
                if (!range.min().isEmpty()) {
                    min = Integer.parseInt(range.min());
                }
                if (!range.max().isEmpty()) {
                    max = Integer.parseInt(range.max());
                }
            } else if (clazz.equals(Long.class)) {
                if (!range.min().isEmpty()) {
                    min = Long.parseLong(range.min());
                }
                if (!range.max().isEmpty()) {
                    max = Long.parseLong(range.max());
                }
            } else if (clazz.equals(Float.class)) {
                if (!range.min().isEmpty()) {
                    min = Float.parseFloat(range.min());
                }
                if (!range.max().isEmpty()) {
                    max = Float.parseFloat(range.max());
                }
            } else if (clazz.equals(Double.class)) {
                if (!range.min().isEmpty()) {
                    min = Double.parseDouble(range.min());
                }
                if (!range.max().isEmpty()) {
                    max = Double.parseDouble(range.max());
                }
            }
            final ParserParameters parserParameters = new ParserParameters();
            if (min != null) {
                parserParameters.store(StandardParameters.RANGE_MIN, min);
            }
            if (max != null) {
                parserParameters.store(StandardParameters.RANGE_MAX, max);
            }
            return parserParameters;
        }

    }


    private static final class CompletionsMapper implements BiFunction<Completions, TypeToken<?>, ParserParameters> {

        @Override
        public ParserParameters apply(final Completions completions, final TypeToken<?> token) {
            if (token.getRawType().equals(String.class)) {
                final String[] splitCompletions = completions.value().replace(" ", "").split(",");
                return ParserParameters.single(StandardParameters.COMPLETIONS, splitCompletions);
            }
            return ParserParameters.empty();
        }

    }

}
