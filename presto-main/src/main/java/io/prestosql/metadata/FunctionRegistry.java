/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.metadata;

import com.google.common.base.Joiner;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.UncheckedExecutionException;
import io.prestosql.operator.aggregation.ApproximateCountDistinctAggregation;
import io.prestosql.operator.aggregation.ApproximateDoublePercentileAggregations;
import io.prestosql.operator.aggregation.ApproximateDoublePercentileArrayAggregations;
import io.prestosql.operator.aggregation.ApproximateLongPercentileAggregations;
import io.prestosql.operator.aggregation.ApproximateLongPercentileArrayAggregations;
import io.prestosql.operator.aggregation.ApproximateRealPercentileAggregations;
import io.prestosql.operator.aggregation.ApproximateRealPercentileArrayAggregations;
import io.prestosql.operator.aggregation.ApproximateSetAggregation;
import io.prestosql.operator.aggregation.AverageAggregations;
import io.prestosql.operator.aggregation.BitwiseAndAggregation;
import io.prestosql.operator.aggregation.BitwiseOrAggregation;
import io.prestosql.operator.aggregation.BooleanAndAggregation;
import io.prestosql.operator.aggregation.BooleanOrAggregation;
import io.prestosql.operator.aggregation.CentralMomentsAggregation;
import io.prestosql.operator.aggregation.CountAggregation;
import io.prestosql.operator.aggregation.CountIfAggregation;
import io.prestosql.operator.aggregation.DefaultApproximateCountDistinctAggregation;
import io.prestosql.operator.aggregation.DoubleCorrelationAggregation;
import io.prestosql.operator.aggregation.DoubleCovarianceAggregation;
import io.prestosql.operator.aggregation.DoubleHistogramAggregation;
import io.prestosql.operator.aggregation.DoubleRegressionAggregation;
import io.prestosql.operator.aggregation.DoubleSumAggregation;
import io.prestosql.operator.aggregation.GeometricMeanAggregations;
import io.prestosql.operator.aggregation.InternalAggregationFunction;
import io.prestosql.operator.aggregation.IntervalDayToSecondAverageAggregation;
import io.prestosql.operator.aggregation.IntervalDayToSecondSumAggregation;
import io.prestosql.operator.aggregation.IntervalYearToMonthAverageAggregation;
import io.prestosql.operator.aggregation.IntervalYearToMonthSumAggregation;
import io.prestosql.operator.aggregation.LongSumAggregation;
import io.prestosql.operator.aggregation.MaxDataSizeForStats;
import io.prestosql.operator.aggregation.MergeHyperLogLogAggregation;
import io.prestosql.operator.aggregation.MergeQuantileDigestFunction;
import io.prestosql.operator.aggregation.RealCorrelationAggregation;
import io.prestosql.operator.aggregation.RealCovarianceAggregation;
import io.prestosql.operator.aggregation.RealGeometricMeanAggregations;
import io.prestosql.operator.aggregation.RealHistogramAggregation;
import io.prestosql.operator.aggregation.RealRegressionAggregation;
import io.prestosql.operator.aggregation.RealSumAggregation;
import io.prestosql.operator.aggregation.SumDataSizeForStats;
import io.prestosql.operator.aggregation.VarianceAggregation;
import io.prestosql.operator.aggregation.arrayagg.ArrayAggregationFunction;
import io.prestosql.operator.aggregation.histogram.Histogram;
import io.prestosql.operator.aggregation.multimapagg.MultimapAggregationFunction;
import io.prestosql.operator.scalar.ArrayAllMatchFunction;
import io.prestosql.operator.scalar.ArrayAnyMatchFunction;
import io.prestosql.operator.scalar.ArrayCardinalityFunction;
import io.prestosql.operator.scalar.ArrayCombinationsFunction;
import io.prestosql.operator.scalar.ArrayContains;
import io.prestosql.operator.scalar.ArrayDistinctFromOperator;
import io.prestosql.operator.scalar.ArrayDistinctFunction;
import io.prestosql.operator.scalar.ArrayElementAtFunction;
import io.prestosql.operator.scalar.ArrayEqualOperator;
import io.prestosql.operator.scalar.ArrayExceptFunction;
import io.prestosql.operator.scalar.ArrayFilterFunction;
import io.prestosql.operator.scalar.ArrayFunctions;
import io.prestosql.operator.scalar.ArrayGreaterThanOperator;
import io.prestosql.operator.scalar.ArrayGreaterThanOrEqualOperator;
import io.prestosql.operator.scalar.ArrayHashCodeOperator;
import io.prestosql.operator.scalar.ArrayIndeterminateOperator;
import io.prestosql.operator.scalar.ArrayIntersectFunction;
import io.prestosql.operator.scalar.ArrayLessThanOperator;
import io.prestosql.operator.scalar.ArrayLessThanOrEqualOperator;
import io.prestosql.operator.scalar.ArrayMaxFunction;
import io.prestosql.operator.scalar.ArrayMinFunction;
import io.prestosql.operator.scalar.ArrayNgramsFunction;
import io.prestosql.operator.scalar.ArrayNoneMatchFunction;
import io.prestosql.operator.scalar.ArrayNotEqualOperator;
import io.prestosql.operator.scalar.ArrayPositionFunction;
import io.prestosql.operator.scalar.ArrayRemoveFunction;
import io.prestosql.operator.scalar.ArrayReverseFunction;
import io.prestosql.operator.scalar.ArrayShuffleFunction;
import io.prestosql.operator.scalar.ArraySliceFunction;
import io.prestosql.operator.scalar.ArraySortComparatorFunction;
import io.prestosql.operator.scalar.ArraySortFunction;
import io.prestosql.operator.scalar.ArrayUnionFunction;
import io.prestosql.operator.scalar.ArraysOverlapFunction;
import io.prestosql.operator.scalar.BitwiseFunctions;
import io.prestosql.operator.scalar.CharacterStringCasts;
import io.prestosql.operator.scalar.ColorFunctions;
import io.prestosql.operator.scalar.CombineHashFunction;
import io.prestosql.operator.scalar.DataSizeFunctions;
import io.prestosql.operator.scalar.DateTimeFunctions;
import io.prestosql.operator.scalar.EmptyMapConstructor;
import io.prestosql.operator.scalar.FailureFunction;
import io.prestosql.operator.scalar.HmacFunctions;
import io.prestosql.operator.scalar.HyperLogLogFunctions;
import io.prestosql.operator.scalar.JoniRegexpCasts;
import io.prestosql.operator.scalar.JoniRegexpFunctions;
import io.prestosql.operator.scalar.JoniRegexpReplaceLambdaFunction;
import io.prestosql.operator.scalar.JsonFunctions;
import io.prestosql.operator.scalar.JsonOperators;
import io.prestosql.operator.scalar.MapCardinalityFunction;
import io.prestosql.operator.scalar.MapDistinctFromOperator;
import io.prestosql.operator.scalar.MapEntriesFunction;
import io.prestosql.operator.scalar.MapEqualOperator;
import io.prestosql.operator.scalar.MapFromEntriesFunction;
import io.prestosql.operator.scalar.MapIndeterminateOperator;
import io.prestosql.operator.scalar.MapKeys;
import io.prestosql.operator.scalar.MapNotEqualOperator;
import io.prestosql.operator.scalar.MapSubscriptOperator;
import io.prestosql.operator.scalar.MapValues;
import io.prestosql.operator.scalar.MathFunctions;
import io.prestosql.operator.scalar.MultimapFromEntriesFunction;
import io.prestosql.operator.scalar.QuantileDigestFunctions;
import io.prestosql.operator.scalar.Re2JRegexpFunctions;
import io.prestosql.operator.scalar.Re2JRegexpReplaceLambdaFunction;
import io.prestosql.operator.scalar.RepeatFunction;
import io.prestosql.operator.scalar.ScalarFunctionImplementation;
import io.prestosql.operator.scalar.ScalarFunctionImplementation.ArgumentProperty;
import io.prestosql.operator.scalar.ScalarFunctionImplementation.ArgumentType;
import io.prestosql.operator.scalar.ScalarFunctionImplementation.ScalarImplementationChoice;
import io.prestosql.operator.scalar.SequenceFunction;
import io.prestosql.operator.scalar.SessionFunctions;
import io.prestosql.operator.scalar.SplitToMapFunction;
import io.prestosql.operator.scalar.SplitToMultimapFunction;
import io.prestosql.operator.scalar.StringFunctions;
import io.prestosql.operator.scalar.TryFunction;
import io.prestosql.operator.scalar.TypeOfFunction;
import io.prestosql.operator.scalar.UrlFunctions;
import io.prestosql.operator.scalar.VarbinaryFunctions;
import io.prestosql.operator.scalar.WilsonInterval;
import io.prestosql.operator.scalar.WordStemFunction;
import io.prestosql.operator.window.CumulativeDistributionFunction;
import io.prestosql.operator.window.DenseRankFunction;
import io.prestosql.operator.window.FirstValueFunction;
import io.prestosql.operator.window.LagFunction;
import io.prestosql.operator.window.LastValueFunction;
import io.prestosql.operator.window.LeadFunction;
import io.prestosql.operator.window.NTileFunction;
import io.prestosql.operator.window.NthValueFunction;
import io.prestosql.operator.window.PercentRankFunction;
import io.prestosql.operator.window.RankFunction;
import io.prestosql.operator.window.RowNumberFunction;
import io.prestosql.operator.window.SqlWindowFunction;
import io.prestosql.operator.window.WindowFunctionSupplier;
import io.prestosql.spi.PrestoException;
import io.prestosql.spi.function.OperatorType;
import io.prestosql.spi.type.Type;
import io.prestosql.sql.DynamicFilters;
import io.prestosql.sql.analyzer.FeaturesConfig;
import io.prestosql.sql.analyzer.TypeSignatureProvider;
import io.prestosql.sql.tree.QualifiedName;
import io.prestosql.type.BigintOperators;
import io.prestosql.type.BooleanOperators;
import io.prestosql.type.CharOperators;
import io.prestosql.type.ColorOperators;
import io.prestosql.type.DateOperators;
import io.prestosql.type.DateTimeOperators;
import io.prestosql.type.DecimalOperators;
import io.prestosql.type.DoubleOperators;
import io.prestosql.type.HyperLogLogOperators;
import io.prestosql.type.IntegerOperators;
import io.prestosql.type.IntervalDayTimeOperators;
import io.prestosql.type.IntervalYearMonthOperators;
import io.prestosql.type.IpAddressOperators;
import io.prestosql.type.LikeFunctions;
import io.prestosql.type.QuantileDigestOperators;
import io.prestosql.type.RealOperators;
import io.prestosql.type.SmallintOperators;
import io.prestosql.type.TimeOperators;
import io.prestosql.type.TimeWithTimeZoneOperators;
import io.prestosql.type.TimestampOperators;
import io.prestosql.type.TimestampWithTimeZoneOperators;
import io.prestosql.type.TinyintOperators;
import io.prestosql.type.UnknownOperators;
import io.prestosql.type.UuidOperators;
import io.prestosql.type.VarbinaryOperators;
import io.prestosql.type.VarcharOperators;
import io.prestosql.type.setdigest.BuildSetDigestAggregation;
import io.prestosql.type.setdigest.MergeSetDigestAggregation;
import io.prestosql.type.setdigest.SetDigestFunctions;
import io.prestosql.type.setdigest.SetDigestOperators;

import javax.annotation.concurrent.ThreadSafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.getOnlyElement;
import static io.prestosql.metadata.FunctionId.toFunctionId;
import static io.prestosql.metadata.FunctionKind.AGGREGATE;
import static io.prestosql.metadata.FunctionKind.SCALAR;
import static io.prestosql.metadata.Signature.mangleOperatorName;
import static io.prestosql.operator.aggregation.ArbitraryAggregationFunction.ARBITRARY_AGGREGATION;
import static io.prestosql.operator.aggregation.ChecksumAggregationFunction.CHECKSUM_AGGREGATION;
import static io.prestosql.operator.aggregation.CountColumn.COUNT_COLUMN;
import static io.prestosql.operator.aggregation.DecimalAverageAggregation.DECIMAL_AVERAGE_AGGREGATION;
import static io.prestosql.operator.aggregation.DecimalSumAggregation.DECIMAL_SUM_AGGREGATION;
import static io.prestosql.operator.aggregation.MapAggregationFunction.MAP_AGG;
import static io.prestosql.operator.aggregation.MapUnionAggregation.MAP_UNION;
import static io.prestosql.operator.aggregation.MaxAggregationFunction.MAX_AGGREGATION;
import static io.prestosql.operator.aggregation.MaxNAggregationFunction.MAX_N_AGGREGATION;
import static io.prestosql.operator.aggregation.MinAggregationFunction.MIN_AGGREGATION;
import static io.prestosql.operator.aggregation.MinNAggregationFunction.MIN_N_AGGREGATION;
import static io.prestosql.operator.aggregation.QuantileDigestAggregationFunction.QDIGEST_AGG;
import static io.prestosql.operator.aggregation.QuantileDigestAggregationFunction.QDIGEST_AGG_WITH_WEIGHT;
import static io.prestosql.operator.aggregation.QuantileDigestAggregationFunction.QDIGEST_AGG_WITH_WEIGHT_AND_ERROR;
import static io.prestosql.operator.aggregation.RealAverageAggregation.REAL_AVERAGE_AGGREGATION;
import static io.prestosql.operator.aggregation.ReduceAggregationFunction.REDUCE_AGG;
import static io.prestosql.operator.aggregation.minmaxby.MaxByAggregationFunction.MAX_BY;
import static io.prestosql.operator.aggregation.minmaxby.MaxByNAggregationFunction.MAX_BY_N_AGGREGATION;
import static io.prestosql.operator.aggregation.minmaxby.MinByAggregationFunction.MIN_BY;
import static io.prestosql.operator.aggregation.minmaxby.MinByNAggregationFunction.MIN_BY_N_AGGREGATION;
import static io.prestosql.operator.scalar.ArrayConcatFunction.ARRAY_CONCAT_FUNCTION;
import static io.prestosql.operator.scalar.ArrayConstructor.ARRAY_CONSTRUCTOR;
import static io.prestosql.operator.scalar.ArrayFlattenFunction.ARRAY_FLATTEN_FUNCTION;
import static io.prestosql.operator.scalar.ArrayJoin.ARRAY_JOIN;
import static io.prestosql.operator.scalar.ArrayJoin.ARRAY_JOIN_WITH_NULL_REPLACEMENT;
import static io.prestosql.operator.scalar.ArrayReduceFunction.ARRAY_REDUCE_FUNCTION;
import static io.prestosql.operator.scalar.ArraySubscriptOperator.ARRAY_SUBSCRIPT;
import static io.prestosql.operator.scalar.ArrayToArrayCast.ARRAY_TO_ARRAY_CAST;
import static io.prestosql.operator.scalar.ArrayToElementConcatFunction.ARRAY_TO_ELEMENT_CONCAT_FUNCTION;
import static io.prestosql.operator.scalar.ArrayToJsonCast.ARRAY_TO_JSON;
import static io.prestosql.operator.scalar.ArrayTransformFunction.ARRAY_TRANSFORM_FUNCTION;
import static io.prestosql.operator.scalar.CastFromUnknownOperator.CAST_FROM_UNKNOWN;
import static io.prestosql.operator.scalar.ConcatFunction.VARBINARY_CONCAT;
import static io.prestosql.operator.scalar.ConcatFunction.VARCHAR_CONCAT;
import static io.prestosql.operator.scalar.ElementToArrayConcatFunction.ELEMENT_TO_ARRAY_CONCAT_FUNCTION;
import static io.prestosql.operator.scalar.FormatFunction.FORMAT_FUNCTION;
import static io.prestosql.operator.scalar.Greatest.GREATEST;
import static io.prestosql.operator.scalar.IdentityCast.IDENTITY_CAST;
import static io.prestosql.operator.scalar.JsonStringToArrayCast.JSON_STRING_TO_ARRAY;
import static io.prestosql.operator.scalar.JsonStringToMapCast.JSON_STRING_TO_MAP;
import static io.prestosql.operator.scalar.JsonStringToRowCast.JSON_STRING_TO_ROW;
import static io.prestosql.operator.scalar.JsonToArrayCast.JSON_TO_ARRAY;
import static io.prestosql.operator.scalar.JsonToMapCast.JSON_TO_MAP;
import static io.prestosql.operator.scalar.JsonToRowCast.JSON_TO_ROW;
import static io.prestosql.operator.scalar.Least.LEAST;
import static io.prestosql.operator.scalar.MapConcatFunction.MAP_CONCAT_FUNCTION;
import static io.prestosql.operator.scalar.MapConstructor.MAP_CONSTRUCTOR;
import static io.prestosql.operator.scalar.MapElementAtFunction.MAP_ELEMENT_AT;
import static io.prestosql.operator.scalar.MapFilterFunction.MAP_FILTER_FUNCTION;
import static io.prestosql.operator.scalar.MapHashCodeOperator.MAP_HASH_CODE;
import static io.prestosql.operator.scalar.MapToJsonCast.MAP_TO_JSON;
import static io.prestosql.operator.scalar.MapToMapCast.MAP_TO_MAP_CAST;
import static io.prestosql.operator.scalar.MapTransformKeyFunction.MAP_TRANSFORM_KEY_FUNCTION;
import static io.prestosql.operator.scalar.MapTransformValueFunction.MAP_TRANSFORM_VALUE_FUNCTION;
import static io.prestosql.operator.scalar.MapZipWithFunction.MAP_ZIP_WITH_FUNCTION;
import static io.prestosql.operator.scalar.MathFunctions.DECIMAL_MOD_FUNCTION;
import static io.prestosql.operator.scalar.Re2JCastToRegexpFunction.castCharToRe2JRegexp;
import static io.prestosql.operator.scalar.Re2JCastToRegexpFunction.castVarcharToRe2JRegexp;
import static io.prestosql.operator.scalar.RowDistinctFromOperator.ROW_DISTINCT_FROM;
import static io.prestosql.operator.scalar.RowEqualOperator.ROW_EQUAL;
import static io.prestosql.operator.scalar.RowGreaterThanOperator.ROW_GREATER_THAN;
import static io.prestosql.operator.scalar.RowGreaterThanOrEqualOperator.ROW_GREATER_THAN_OR_EQUAL;
import static io.prestosql.operator.scalar.RowHashCodeOperator.ROW_HASH_CODE;
import static io.prestosql.operator.scalar.RowIndeterminateOperator.ROW_INDETERMINATE;
import static io.prestosql.operator.scalar.RowLessThanOperator.ROW_LESS_THAN;
import static io.prestosql.operator.scalar.RowLessThanOrEqualOperator.ROW_LESS_THAN_OR_EQUAL;
import static io.prestosql.operator.scalar.RowNotEqualOperator.ROW_NOT_EQUAL;
import static io.prestosql.operator.scalar.RowToJsonCast.ROW_TO_JSON;
import static io.prestosql.operator.scalar.RowToRowCast.ROW_TO_ROW_CAST;
import static io.prestosql.operator.scalar.ScalarFunctionImplementation.NullConvention.BLOCK_AND_POSITION;
import static io.prestosql.operator.scalar.ScalarFunctionImplementation.NullConvention.RETURN_NULL_ON_NULL;
import static io.prestosql.operator.scalar.TryCastFunction.TRY_CAST;
import static io.prestosql.operator.scalar.ZipFunction.ZIP_FUNCTIONS;
import static io.prestosql.operator.scalar.ZipWithFunction.ZIP_WITH_FUNCTION;
import static io.prestosql.operator.window.AggregateWindowFunction.supplier;
import static io.prestosql.spi.StandardErrorCode.AMBIGUOUS_FUNCTION_CALL;
import static io.prestosql.spi.StandardErrorCode.FUNCTION_IMPLEMENTATION_MISSING;
import static io.prestosql.spi.StandardErrorCode.FUNCTION_NOT_FOUND;
import static io.prestosql.sql.analyzer.TypeSignatureProvider.fromTypeSignatures;
import static io.prestosql.sql.analyzer.TypeSignatureProvider.fromTypes;
import static io.prestosql.type.DecimalCasts.BIGINT_TO_DECIMAL_CAST;
import static io.prestosql.type.DecimalCasts.BOOLEAN_TO_DECIMAL_CAST;
import static io.prestosql.type.DecimalCasts.DECIMAL_TO_BIGINT_CAST;
import static io.prestosql.type.DecimalCasts.DECIMAL_TO_BOOLEAN_CAST;
import static io.prestosql.type.DecimalCasts.DECIMAL_TO_DOUBLE_CAST;
import static io.prestosql.type.DecimalCasts.DECIMAL_TO_INTEGER_CAST;
import static io.prestosql.type.DecimalCasts.DECIMAL_TO_JSON_CAST;
import static io.prestosql.type.DecimalCasts.DECIMAL_TO_REAL_CAST;
import static io.prestosql.type.DecimalCasts.DECIMAL_TO_SMALLINT_CAST;
import static io.prestosql.type.DecimalCasts.DECIMAL_TO_TINYINT_CAST;
import static io.prestosql.type.DecimalCasts.DECIMAL_TO_VARCHAR_CAST;
import static io.prestosql.type.DecimalCasts.DOUBLE_TO_DECIMAL_CAST;
import static io.prestosql.type.DecimalCasts.INTEGER_TO_DECIMAL_CAST;
import static io.prestosql.type.DecimalCasts.JSON_TO_DECIMAL_CAST;
import static io.prestosql.type.DecimalCasts.REAL_TO_DECIMAL_CAST;
import static io.prestosql.type.DecimalCasts.SMALLINT_TO_DECIMAL_CAST;
import static io.prestosql.type.DecimalCasts.TINYINT_TO_DECIMAL_CAST;
import static io.prestosql.type.DecimalCasts.VARCHAR_TO_DECIMAL_CAST;
import static io.prestosql.type.DecimalInequalityOperators.DECIMAL_BETWEEN_OPERATOR;
import static io.prestosql.type.DecimalInequalityOperators.DECIMAL_DISTINCT_FROM_OPERATOR;
import static io.prestosql.type.DecimalInequalityOperators.DECIMAL_EQUAL_OPERATOR;
import static io.prestosql.type.DecimalInequalityOperators.DECIMAL_GREATER_THAN_OPERATOR;
import static io.prestosql.type.DecimalInequalityOperators.DECIMAL_GREATER_THAN_OR_EQUAL_OPERATOR;
import static io.prestosql.type.DecimalInequalityOperators.DECIMAL_LESS_THAN_OPERATOR;
import static io.prestosql.type.DecimalInequalityOperators.DECIMAL_LESS_THAN_OR_EQUAL_OPERATOR;
import static io.prestosql.type.DecimalInequalityOperators.DECIMAL_NOT_EQUAL_OPERATOR;
import static io.prestosql.type.DecimalOperators.DECIMAL_ADD_OPERATOR;
import static io.prestosql.type.DecimalOperators.DECIMAL_DIVIDE_OPERATOR;
import static io.prestosql.type.DecimalOperators.DECIMAL_MODULUS_OPERATOR;
import static io.prestosql.type.DecimalOperators.DECIMAL_MULTIPLY_OPERATOR;
import static io.prestosql.type.DecimalOperators.DECIMAL_SUBTRACT_OPERATOR;
import static io.prestosql.type.DecimalSaturatedFloorCasts.BIGINT_TO_DECIMAL_SATURATED_FLOOR_CAST;
import static io.prestosql.type.DecimalSaturatedFloorCasts.DECIMAL_TO_BIGINT_SATURATED_FLOOR_CAST;
import static io.prestosql.type.DecimalSaturatedFloorCasts.DECIMAL_TO_DECIMAL_SATURATED_FLOOR_CAST;
import static io.prestosql.type.DecimalSaturatedFloorCasts.DECIMAL_TO_INTEGER_SATURATED_FLOOR_CAST;
import static io.prestosql.type.DecimalSaturatedFloorCasts.DECIMAL_TO_SMALLINT_SATURATED_FLOOR_CAST;
import static io.prestosql.type.DecimalSaturatedFloorCasts.DECIMAL_TO_TINYINT_SATURATED_FLOOR_CAST;
import static io.prestosql.type.DecimalSaturatedFloorCasts.INTEGER_TO_DECIMAL_SATURATED_FLOOR_CAST;
import static io.prestosql.type.DecimalSaturatedFloorCasts.SMALLINT_TO_DECIMAL_SATURATED_FLOOR_CAST;
import static io.prestosql.type.DecimalSaturatedFloorCasts.TINYINT_TO_DECIMAL_SATURATED_FLOOR_CAST;
import static io.prestosql.type.DecimalToDecimalCasts.DECIMAL_TO_DECIMAL_CAST;
import static io.prestosql.type.UnknownType.UNKNOWN;
import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.Collections.nCopies;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.HOURS;

@ThreadSafe
public class FunctionRegistry
{
    private final Metadata metadata;
    private final LoadingCache<SpecializedFunctionKey, ScalarFunctionImplementation> specializedScalarCache;
    private final LoadingCache<SpecializedFunctionKey, InternalAggregationFunction> specializedAggregationCache;
    private final LoadingCache<SpecializedFunctionKey, WindowFunctionSupplier> specializedWindowCache;
    private volatile FunctionMap functions = new FunctionMap();

    public FunctionRegistry(Metadata metadata, FeaturesConfig featuresConfig)
    {
        this.metadata = requireNonNull(metadata, "metadata is null");

        // TODO the function map should be updated, so that this cast can be removed

        // We have observed repeated compilation of MethodHandle that leads to full GCs.
        // We notice that flushing the following caches mitigate the problem.
        // We suspect that it is a JVM bug that is related to stale/corrupted profiling data associated
        // with generated classes and/or dynamically-created MethodHandles.
        // This might also mitigate problems like deoptimization storm or unintended interpreted execution.

        specializedScalarCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, HOURS)
                .build(CacheLoader.from(key -> {
                    SqlScalarFunction function = (SqlScalarFunction) key.getFunction();
                    ScalarFunctionImplementation specialize = function.specialize(key.getBoundVariables(), key.getArity(), metadata);
                    FunctionMetadata functionMetadata = function.getFunctionMetadata();
                    for (ScalarImplementationChoice choice : specialize.getAllChoices()) {
                        checkArgument(choice.isNullable() == functionMetadata.isNullable(), "choice nullability doesn't match for: " + functionMetadata.getSignature());
                        for (int i = 0; i < choice.getArgumentProperties().size(); i++) {
                            ArgumentProperty argumentProperty = choice.getArgumentProperty(i);
                            int functionArgumentIndex = i;
                            if (functionMetadata.getSignature().isVariableArity()) {
                                functionArgumentIndex = min(i, functionMetadata.getSignature().getArgumentTypes().size() - 1);
                            }
                            boolean functionPropertyNullability = functionMetadata.getArgumentDefinitions().get(functionArgumentIndex).isNullable();
                            if (argumentProperty.getArgumentType() == ArgumentType.FUNCTION_TYPE) {
                                checkArgument(!functionPropertyNullability, "choice function argument must not be nullable: " + functionMetadata.getSignature());
                            }
                            else if (argumentProperty.getNullConvention() != BLOCK_AND_POSITION) {
                                boolean choiceNullability = argumentProperty.getNullConvention() != RETURN_NULL_ON_NULL;
                                checkArgument(functionPropertyNullability == choiceNullability, "choice function argument nullability doesn't match for: " + functionMetadata.getSignature());
                            }
                        }
                    }
                    return specialize;
                }));

        specializedAggregationCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, HOURS)
                .build(CacheLoader.from(key -> {
                    SqlAggregationFunction function = (SqlAggregationFunction) key.getFunction();
                    InternalAggregationFunction implementation = function.specialize(key.getBoundVariables(), key.getArity(), metadata);
                    checkArgument(function.isOrderSensitive() == implementation.isOrderSensitive(), "implementation order sensitivity doesn't match for: %s", function.getFunctionMetadata().getSignature());
                    checkArgument(function.isDecomposable() == implementation.isDecomposable(), "implementation decomposable doesn't match for: %s", function.getFunctionMetadata().getSignature());
                    return implementation;
                }));

        specializedWindowCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, HOURS)
                .build(CacheLoader.from(key -> {
                    if (key.getFunction() instanceof SqlAggregationFunction) {
                        return supplier(key.getFunction().getFunctionMetadata().getSignature(), specializedAggregationCache.getUnchecked(key));
                    }
                    return ((SqlWindowFunction) key.getFunction())
                            .specialize(key.getBoundVariables(), key.getArity(), metadata);
                }));

        FunctionListBuilder builder = new FunctionListBuilder()
                .window(RowNumberFunction.class)
                .window(RankFunction.class)
                .window(DenseRankFunction.class)
                .window(PercentRankFunction.class)
                .window(CumulativeDistributionFunction.class)
                .window(NTileFunction.class)
                .window(FirstValueFunction.class)
                .window(LastValueFunction.class)
                .window(NthValueFunction.class)
                .window(LagFunction.class)
                .window(LeadFunction.class)
                .aggregate(ApproximateCountDistinctAggregation.class)
                .aggregate(DefaultApproximateCountDistinctAggregation.class)
                .aggregate(SumDataSizeForStats.class)
                .aggregate(MaxDataSizeForStats.class)
                .aggregates(CountAggregation.class)
                .aggregates(VarianceAggregation.class)
                .aggregates(CentralMomentsAggregation.class)
                .aggregates(ApproximateLongPercentileAggregations.class)
                .aggregates(ApproximateLongPercentileArrayAggregations.class)
                .aggregates(ApproximateDoublePercentileAggregations.class)
                .aggregates(ApproximateDoublePercentileArrayAggregations.class)
                .aggregates(ApproximateRealPercentileAggregations.class)
                .aggregates(ApproximateRealPercentileArrayAggregations.class)
                .aggregates(CountIfAggregation.class)
                .aggregates(BooleanAndAggregation.class)
                .aggregates(BooleanOrAggregation.class)
                .aggregates(DoubleSumAggregation.class)
                .aggregates(RealSumAggregation.class)
                .aggregates(LongSumAggregation.class)
                .aggregates(IntervalDayToSecondSumAggregation.class)
                .aggregates(IntervalYearToMonthSumAggregation.class)
                .aggregates(AverageAggregations.class)
                .function(REAL_AVERAGE_AGGREGATION)
                .aggregates(IntervalDayToSecondAverageAggregation.class)
                .aggregates(IntervalYearToMonthAverageAggregation.class)
                .aggregates(GeometricMeanAggregations.class)
                .aggregates(RealGeometricMeanAggregations.class)
                .aggregates(MergeHyperLogLogAggregation.class)
                .aggregates(ApproximateSetAggregation.class)
                .functions(QDIGEST_AGG, QDIGEST_AGG_WITH_WEIGHT, QDIGEST_AGG_WITH_WEIGHT_AND_ERROR)
                .function(MergeQuantileDigestFunction.MERGE)
                .aggregates(DoubleHistogramAggregation.class)
                .aggregates(RealHistogramAggregation.class)
                .aggregates(DoubleCovarianceAggregation.class)
                .aggregates(RealCovarianceAggregation.class)
                .aggregates(DoubleRegressionAggregation.class)
                .aggregates(RealRegressionAggregation.class)
                .aggregates(DoubleCorrelationAggregation.class)
                .aggregates(RealCorrelationAggregation.class)
                .aggregates(BitwiseOrAggregation.class)
                .aggregates(BitwiseAndAggregation.class)
                .scalar(RepeatFunction.class)
                .scalars(SequenceFunction.class)
                .scalars(SessionFunctions.class)
                .scalars(StringFunctions.class)
                .scalars(WordStemFunction.class)
                .scalar(SplitToMapFunction.class)
                .scalar(SplitToMultimapFunction.class)
                .scalars(VarbinaryFunctions.class)
                .scalars(UrlFunctions.class)
                .scalars(MathFunctions.class)
                .scalar(MathFunctions.Abs.class)
                .scalar(MathFunctions.Sign.class)
                .scalar(MathFunctions.Round.class)
                .scalar(MathFunctions.RoundN.class)
                .scalar(MathFunctions.Truncate.class)
                .scalar(MathFunctions.TruncateN.class)
                .scalar(MathFunctions.Ceiling.class)
                .scalar(MathFunctions.Floor.class)
                .scalars(BitwiseFunctions.class)
                .scalars(DateTimeFunctions.class)
                .scalars(JsonFunctions.class)
                .scalars(ColorFunctions.class)
                .scalars(ColorOperators.class)
                .scalar(ColorOperators.ColorDistinctFromOperator.class)
                .scalars(HyperLogLogFunctions.class)
                .scalars(QuantileDigestFunctions.class)
                .scalars(UnknownOperators.class)
                .scalar(UnknownOperators.UnknownDistinctFromOperator.class)
                .scalars(BooleanOperators.class)
                .scalar(BooleanOperators.BooleanDistinctFromOperator.class)
                .scalars(BigintOperators.class)
                .scalar(BigintOperators.BigintDistinctFromOperator.class)
                .scalars(IntegerOperators.class)
                .scalar(IntegerOperators.IntegerDistinctFromOperator.class)
                .scalars(SmallintOperators.class)
                .scalar(SmallintOperators.SmallintDistinctFromOperator.class)
                .scalars(TinyintOperators.class)
                .scalar(TinyintOperators.TinyintDistinctFromOperator.class)
                .scalars(DoubleOperators.class)
                .scalar(DoubleOperators.DoubleDistinctFromOperator.class)
                .scalars(RealOperators.class)
                .scalar(RealOperators.RealDistinctFromOperator.class)
                .scalars(VarcharOperators.class)
                .scalar(VarcharOperators.VarcharDistinctFromOperator.class)
                .scalars(VarbinaryOperators.class)
                .scalar(VarbinaryOperators.VarbinaryDistinctFromOperator.class)
                .scalars(DateOperators.class)
                .scalar(DateOperators.DateDistinctFromOperator.class)
                .scalars(TimeOperators.class)
                .scalar(TimeOperators.TimeDistinctFromOperator.class)
                .scalars(TimestampOperators.class)
                .scalar(TimestampOperators.TimestampDistinctFromOperator.class)
                .scalars(IntervalDayTimeOperators.class)
                .scalar(IntervalDayTimeOperators.IntervalDayTimeDistinctFromOperator.class)
                .scalars(IntervalYearMonthOperators.class)
                .scalar(IntervalYearMonthOperators.IntervalYearMonthDistinctFromOperator.class)
                .scalars(TimeWithTimeZoneOperators.class)
                .scalar(TimeWithTimeZoneOperators.TimeWithTimeZoneDistinctFromOperator.class)
                .scalars(TimestampWithTimeZoneOperators.class)
                .scalar(TimestampWithTimeZoneOperators.TimestampWithTimeZoneDistinctFromOperator.class)
                .scalars(DateTimeOperators.class)
                .scalars(HyperLogLogOperators.class)
                .scalars(QuantileDigestOperators.class)
                .scalars(IpAddressOperators.class)
                .scalar(IpAddressOperators.IpAddressDistinctFromOperator.class)
                .scalars(UuidOperators.class)
                .scalar(UuidOperators.UuidDistinctFromOperator.class)
                .scalars(LikeFunctions.class)
                .scalars(ArrayFunctions.class)
                .scalars(HmacFunctions.class)
                .scalars(DataSizeFunctions.class)
                .scalar(ArrayCardinalityFunction.class)
                .scalar(ArrayContains.class)
                .scalar(ArrayFilterFunction.class)
                .scalar(ArrayPositionFunction.class)
                .scalars(CombineHashFunction.class)
                .scalars(JsonOperators.class)
                .scalar(JsonOperators.JsonDistinctFromOperator.class)
                .scalars(FailureFunction.class)
                .scalars(JoniRegexpCasts.class)
                .scalars(CharacterStringCasts.class)
                .scalars(CharOperators.class)
                .scalar(CharOperators.CharDistinctFromOperator.class)
                .scalar(DecimalOperators.Negation.class)
                .scalar(DecimalOperators.HashCode.class)
                .scalar(DecimalOperators.Indeterminate.class)
                .scalar(DecimalOperators.XxHash64Operator.class)
                .functions(IDENTITY_CAST, CAST_FROM_UNKNOWN)
                .scalar(ArrayLessThanOperator.class)
                .scalar(ArrayLessThanOrEqualOperator.class)
                .scalar(ArrayRemoveFunction.class)
                .scalar(ArrayGreaterThanOperator.class)
                .scalar(ArrayGreaterThanOrEqualOperator.class)
                .scalar(ArrayElementAtFunction.class)
                .scalar(ArraySortFunction.class)
                .scalar(ArraySortComparatorFunction.class)
                .scalar(ArrayShuffleFunction.class)
                .scalar(ArrayReverseFunction.class)
                .scalar(ArrayMinFunction.class)
                .scalar(ArrayMaxFunction.class)
                .scalar(ArrayDistinctFunction.class)
                .scalar(ArrayNotEqualOperator.class)
                .scalar(ArrayEqualOperator.class)
                .scalar(ArrayHashCodeOperator.class)
                .scalar(ArrayIntersectFunction.class)
                .scalar(ArraysOverlapFunction.class)
                .scalar(ArrayDistinctFromOperator.class)
                .scalar(ArrayUnionFunction.class)
                .scalar(ArrayExceptFunction.class)
                .scalar(ArraySliceFunction.class)
                .scalar(ArrayIndeterminateOperator.class)
                .scalar(ArrayCombinationsFunction.class)
                .scalar(ArrayNgramsFunction.class)
                .scalar(ArrayAllMatchFunction.class)
                .scalar(ArrayAnyMatchFunction.class)
                .scalar(ArrayNoneMatchFunction.class)
                .scalar(MapDistinctFromOperator.class)
                .scalar(MapEqualOperator.class)
                .scalar(MapEntriesFunction.class)
                .scalar(MapFromEntriesFunction.class)
                .scalar(MultimapFromEntriesFunction.class)
                .scalar(MapNotEqualOperator.class)
                .scalar(MapKeys.class)
                .scalar(MapValues.class)
                .scalar(MapCardinalityFunction.class)
                .scalar(EmptyMapConstructor.class)
                .scalar(MapIndeterminateOperator.class)
                .scalar(TypeOfFunction.class)
                .scalar(TryFunction.class)
                .scalar(DynamicFilters.Function.class)
                .functions(ZIP_WITH_FUNCTION, MAP_ZIP_WITH_FUNCTION)
                .functions(ZIP_FUNCTIONS)
                .functions(ARRAY_JOIN, ARRAY_JOIN_WITH_NULL_REPLACEMENT)
                .functions(ARRAY_TO_ARRAY_CAST)
                .functions(ARRAY_TO_ELEMENT_CONCAT_FUNCTION, ELEMENT_TO_ARRAY_CONCAT_FUNCTION)
                .function(MAP_HASH_CODE)
                .function(MAP_ELEMENT_AT)
                .function(MAP_CONCAT_FUNCTION)
                .function(MAP_TO_MAP_CAST)
                .function(ARRAY_FLATTEN_FUNCTION)
                .function(ARRAY_CONCAT_FUNCTION)
                .functions(ARRAY_CONSTRUCTOR, ARRAY_SUBSCRIPT, ARRAY_TO_JSON, JSON_TO_ARRAY, JSON_STRING_TO_ARRAY)
                .function(new ArrayAggregationFunction(featuresConfig.getArrayAggGroupImplementation()))
                .functions(new MapSubscriptOperator())
                .functions(MAP_CONSTRUCTOR, MAP_TO_JSON, JSON_TO_MAP, JSON_STRING_TO_MAP)
                .functions(MAP_AGG, MAP_UNION)
                .function(REDUCE_AGG)
                .function(new MultimapAggregationFunction(featuresConfig.getMultimapAggGroupImplementation()))
                .functions(DECIMAL_TO_VARCHAR_CAST, DECIMAL_TO_INTEGER_CAST, DECIMAL_TO_BIGINT_CAST, DECIMAL_TO_DOUBLE_CAST, DECIMAL_TO_REAL_CAST, DECIMAL_TO_BOOLEAN_CAST, DECIMAL_TO_TINYINT_CAST, DECIMAL_TO_SMALLINT_CAST)
                .functions(VARCHAR_TO_DECIMAL_CAST, INTEGER_TO_DECIMAL_CAST, BIGINT_TO_DECIMAL_CAST, DOUBLE_TO_DECIMAL_CAST, REAL_TO_DECIMAL_CAST, BOOLEAN_TO_DECIMAL_CAST, TINYINT_TO_DECIMAL_CAST, SMALLINT_TO_DECIMAL_CAST)
                .functions(JSON_TO_DECIMAL_CAST, DECIMAL_TO_JSON_CAST)
                .functions(DECIMAL_ADD_OPERATOR, DECIMAL_SUBTRACT_OPERATOR, DECIMAL_MULTIPLY_OPERATOR, DECIMAL_DIVIDE_OPERATOR, DECIMAL_MODULUS_OPERATOR)
                .functions(DECIMAL_EQUAL_OPERATOR, DECIMAL_NOT_EQUAL_OPERATOR)
                .functions(DECIMAL_LESS_THAN_OPERATOR, DECIMAL_LESS_THAN_OR_EQUAL_OPERATOR)
                .functions(DECIMAL_GREATER_THAN_OPERATOR, DECIMAL_GREATER_THAN_OR_EQUAL_OPERATOR)
                .function(DECIMAL_TO_DECIMAL_SATURATED_FLOOR_CAST)
                .functions(DECIMAL_TO_BIGINT_SATURATED_FLOOR_CAST, BIGINT_TO_DECIMAL_SATURATED_FLOOR_CAST)
                .functions(DECIMAL_TO_INTEGER_SATURATED_FLOOR_CAST, INTEGER_TO_DECIMAL_SATURATED_FLOOR_CAST)
                .functions(DECIMAL_TO_SMALLINT_SATURATED_FLOOR_CAST, SMALLINT_TO_DECIMAL_SATURATED_FLOOR_CAST)
                .functions(DECIMAL_TO_TINYINT_SATURATED_FLOOR_CAST, TINYINT_TO_DECIMAL_SATURATED_FLOOR_CAST)
                .function(DECIMAL_BETWEEN_OPERATOR)
                .function(DECIMAL_DISTINCT_FROM_OPERATOR)
                .function(new Histogram(featuresConfig.getHistogramGroupImplementation()))
                .function(CHECKSUM_AGGREGATION)
                .function(ARBITRARY_AGGREGATION)
                .functions(GREATEST, LEAST)
                .functions(MAX_BY, MIN_BY, MAX_BY_N_AGGREGATION, MIN_BY_N_AGGREGATION)
                .functions(MAX_AGGREGATION, MIN_AGGREGATION, MAX_N_AGGREGATION, MIN_N_AGGREGATION)
                .function(COUNT_COLUMN)
                .functions(ROW_HASH_CODE, ROW_TO_JSON, JSON_TO_ROW, JSON_STRING_TO_ROW, ROW_DISTINCT_FROM, ROW_EQUAL, ROW_GREATER_THAN, ROW_GREATER_THAN_OR_EQUAL, ROW_LESS_THAN, ROW_LESS_THAN_OR_EQUAL, ROW_NOT_EQUAL, ROW_TO_ROW_CAST, ROW_INDETERMINATE)
                .functions(VARCHAR_CONCAT, VARBINARY_CONCAT)
                .function(DECIMAL_TO_DECIMAL_CAST)
                .function(castVarcharToRe2JRegexp(featuresConfig.getRe2JDfaStatesLimit(), featuresConfig.getRe2JDfaRetries()))
                .function(castCharToRe2JRegexp(featuresConfig.getRe2JDfaStatesLimit(), featuresConfig.getRe2JDfaRetries()))
                .function(DECIMAL_AVERAGE_AGGREGATION)
                .function(DECIMAL_SUM_AGGREGATION)
                .function(DECIMAL_MOD_FUNCTION)
                .functions(ARRAY_TRANSFORM_FUNCTION, ARRAY_REDUCE_FUNCTION)
                .functions(MAP_FILTER_FUNCTION, MAP_TRANSFORM_KEY_FUNCTION, MAP_TRANSFORM_VALUE_FUNCTION)
                .function(FORMAT_FUNCTION)
                .function(TRY_CAST)
                .function(new LiteralFunction())
                .aggregate(MergeSetDigestAggregation.class)
                .aggregate(BuildSetDigestAggregation.class)
                .scalars(SetDigestFunctions.class)
                .scalars(SetDigestOperators.class)
                .scalars(WilsonInterval.class);

        switch (featuresConfig.getRegexLibrary()) {
            case JONI:
                builder.scalars(JoniRegexpFunctions.class);
                builder.scalar(JoniRegexpReplaceLambdaFunction.class);
                break;
            case RE2J:
                builder.scalars(Re2JRegexpFunctions.class);
                builder.scalar(Re2JRegexpReplaceLambdaFunction.class);
                break;
        }

        addFunctions(builder.getFunctions());
    }

    public final synchronized void addFunctions(List<? extends SqlFunction> functions)
    {
        for (SqlFunction function : functions) {
            FunctionMetadata functionMetadata = function.getFunctionMetadata();
            checkArgument(!functionMetadata.getSignature().getName().contains("|"), "Function name can not contain '|' character: %s", functionMetadata.getSignature());
            for (FunctionMetadata existingFunction : this.functions.list()) {
                checkArgument(!functionMetadata.getFunctionId().equals(existingFunction.getFunctionId()), "Function already registered: %s", functionMetadata.getFunctionId());
                checkArgument(!functionMetadata.getSignature().equals(existingFunction.getSignature()), "Function already registered: %s", functionMetadata.getSignature());
            }
        }
        this.functions = new FunctionMap(this.functions, functions);
    }

    public List<FunctionMetadata> list()
    {
        return functions.list();
    }

    public boolean isAggregationFunction(QualifiedName name)
    {
        return functions.get(name).stream()
                .map(FunctionMetadata::getKind)
                .anyMatch(AGGREGATE::equals);
    }

    ResolvedFunction resolveFunction(QualifiedName name, List<TypeSignatureProvider> parameterTypes)
    {
        Collection<FunctionMetadata> allCandidates = functions.get(name);
        List<FunctionMetadata> exactCandidates = allCandidates.stream()
                .filter(function -> function.getSignature().getTypeVariableConstraints().isEmpty())
                .collect(Collectors.toList());

        Optional<ResolvedFunction> match = matchFunctionExact(exactCandidates, parameterTypes);
        if (match.isPresent()) {
            return match.get();
        }

        List<FunctionMetadata> genericCandidates = allCandidates.stream()
                .filter(function -> !function.getSignature().getTypeVariableConstraints().isEmpty())
                .collect(Collectors.toList());

        match = matchFunctionExact(genericCandidates, parameterTypes);
        if (match.isPresent()) {
            return match.get();
        }

        match = matchFunctionWithCoercion(allCandidates, parameterTypes);
        if (match.isPresent()) {
            return match.get();
        }

        List<String> expectedParameters = new ArrayList<>();
        for (FunctionMetadata function : allCandidates) {
            expectedParameters.add(format("%s(%s) %s",
                    name,
                    Joiner.on(", ").join(function.getSignature().getArgumentTypes()),
                    Joiner.on(", ").join(function.getSignature().getTypeVariableConstraints())));
        }
        String parameters = Joiner.on(", ").join(parameterTypes);
        String message = format("Function %s not registered", name);
        if (!expectedParameters.isEmpty()) {
            String expected = Joiner.on(", ").join(expectedParameters);
            message = format("Unexpected parameters (%s) for function %s. Expected: %s", parameters, name, expected);
        }

        throw new PrestoException(FUNCTION_NOT_FOUND, message);
    }

    private Optional<ResolvedFunction> matchFunctionExact(List<FunctionMetadata> candidates, List<TypeSignatureProvider> actualParameters)
    {
        return matchFunction(candidates, actualParameters, false);
    }

    private Optional<ResolvedFunction> matchFunctionWithCoercion(Collection<FunctionMetadata> candidates, List<TypeSignatureProvider> actualParameters)
    {
        return matchFunction(candidates, actualParameters, true);
    }

    private Optional<ResolvedFunction> matchFunction(Collection<FunctionMetadata> candidates, List<TypeSignatureProvider> parameters, boolean coercionAllowed)
    {
        List<ApplicableFunction> applicableFunctions = identifyApplicableFunctions(candidates, parameters, coercionAllowed);
        if (applicableFunctions.isEmpty()) {
            return Optional.empty();
        }

        if (coercionAllowed) {
            applicableFunctions = selectMostSpecificFunctions(applicableFunctions, parameters);
            checkState(!applicableFunctions.isEmpty(), "at least single function must be left");
        }

        if (applicableFunctions.size() == 1) {
            return Optional.of(getOnlyElement(applicableFunctions).getResolvedFunction());
        }

        StringBuilder errorMessageBuilder = new StringBuilder();
        errorMessageBuilder.append("Could not choose a best candidate operator. Explicit type casts must be added.\n");
        errorMessageBuilder.append("Candidates are:\n");
        for (ApplicableFunction function : applicableFunctions) {
            errorMessageBuilder.append("\t * ");
            errorMessageBuilder.append(function.getBoundSignature());
            errorMessageBuilder.append("\n");
        }
        throw new PrestoException(AMBIGUOUS_FUNCTION_CALL, errorMessageBuilder.toString());
    }

    private List<ApplicableFunction> identifyApplicableFunctions(Collection<FunctionMetadata> candidates, List<TypeSignatureProvider> actualParameters, boolean allowCoercion)
    {
        ImmutableList.Builder<ApplicableFunction> applicableFunctions = ImmutableList.builder();
        for (FunctionMetadata function : candidates) {
            new SignatureBinder(metadata, function.getSignature(), allowCoercion)
                    .bind(actualParameters)
                    .ifPresent(signature -> applicableFunctions.add(new ApplicableFunction(function.getFunctionId(), function.getSignature(), signature)));
        }
        return applicableFunctions.build();
    }

    private List<ApplicableFunction> selectMostSpecificFunctions(List<ApplicableFunction> applicableFunctions, List<TypeSignatureProvider> parameters)
    {
        checkArgument(!applicableFunctions.isEmpty());

        List<ApplicableFunction> mostSpecificFunctions = selectMostSpecificFunctions(applicableFunctions);
        if (mostSpecificFunctions.size() <= 1) {
            return mostSpecificFunctions;
        }

        Optional<List<Type>> optionalParameterTypes = toTypes(parameters);
        if (!optionalParameterTypes.isPresent()) {
            // give up and return all remaining matches
            return mostSpecificFunctions;
        }

        List<Type> parameterTypes = optionalParameterTypes.get();
        if (!someParameterIsUnknown(parameterTypes)) {
            // give up and return all remaining matches
            return mostSpecificFunctions;
        }

        // look for functions that only cast the unknown arguments
        List<ApplicableFunction> unknownOnlyCastFunctions = getUnknownOnlyCastFunctions(applicableFunctions, parameterTypes);
        if (!unknownOnlyCastFunctions.isEmpty()) {
            mostSpecificFunctions = unknownOnlyCastFunctions;
            if (mostSpecificFunctions.size() == 1) {
                return mostSpecificFunctions;
            }
        }

        // If the return type for all the selected function is the same, and the parameters are declared as RETURN_NULL_ON_NULL
        // all the functions are semantically the same. We can return just any of those.
        if (returnTypeIsTheSame(mostSpecificFunctions) && allReturnNullOnGivenInputTypes(mostSpecificFunctions, parameterTypes)) {
            // make it deterministic
            ApplicableFunction selectedFunction = Ordering.usingToString()
                    .reverse()
                    .sortedCopy(mostSpecificFunctions)
                    .get(0);
            return ImmutableList.of(selectedFunction);
        }

        return mostSpecificFunctions;
    }

    private List<ApplicableFunction> selectMostSpecificFunctions(List<ApplicableFunction> candidates)
    {
        List<ApplicableFunction> representatives = new ArrayList<>();

        for (ApplicableFunction current : candidates) {
            boolean found = false;
            for (int i = 0; i < representatives.size(); i++) {
                ApplicableFunction representative = representatives.get(i);
                if (isMoreSpecificThan(current, representative)) {
                    representatives.set(i, current);
                }
                if (isMoreSpecificThan(current, representative) || isMoreSpecificThan(representative, current)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                representatives.add(current);
            }
        }

        return representatives;
    }

    private static boolean someParameterIsUnknown(List<Type> parameters)
    {
        return parameters.stream().anyMatch(type -> type.equals(UNKNOWN));
    }

    private List<ApplicableFunction> getUnknownOnlyCastFunctions(List<ApplicableFunction> applicableFunction, List<Type> actualParameters)
    {
        return applicableFunction.stream()
                .filter((function) -> onlyCastsUnknown(function, actualParameters))
                .collect(toImmutableList());
    }

    private boolean onlyCastsUnknown(ApplicableFunction applicableFunction, List<Type> actualParameters)
    {
        List<Type> boundTypes = applicableFunction.getBoundSignature().getArgumentTypes().stream()
                .map(metadata::getType)
                .collect(toImmutableList());
        checkState(actualParameters.size() == boundTypes.size(), "type lists are of different lengths");
        for (int i = 0; i < actualParameters.size(); i++) {
            if (!boundTypes.get(i).equals(actualParameters.get(i)) && actualParameters.get(i) != UNKNOWN) {
                return false;
            }
        }
        return true;
    }

    private boolean returnTypeIsTheSame(List<ApplicableFunction> applicableFunctions)
    {
        Set<Type> returnTypes = applicableFunctions.stream()
                .map(function -> metadata.getType(function.getBoundSignature().getReturnType()))
                .collect(Collectors.toSet());
        return returnTypes.size() == 1;
    }

    private boolean allReturnNullOnGivenInputTypes(List<ApplicableFunction> applicableFunctions, List<Type> parameters)
    {
        return applicableFunctions.stream().allMatch(x -> returnsNullOnGivenInputTypes(x, parameters));
    }

    private boolean returnsNullOnGivenInputTypes(ApplicableFunction applicableFunction, List<Type> parameterTypes)
    {
        ResolvedFunction resolvedFunction = applicableFunction.getResolvedFunction();
        FunctionMetadata functionMetadata = getSpecializedFunctionKey(resolvedFunction).getFunction().getFunctionMetadata();
        // Window and Aggregation functions have fixed semantic where NULL values are always skipped
        if (functionMetadata.getKind() != SCALAR) {
            return true;
        }

        List<FunctionArgumentDefinition> argumentDefinitions = functionMetadata.getArgumentDefinitions();
        for (int i = 0; i < parameterTypes.size(); i++) {
            // if the argument value will always be null and the function argument is not nullable, the function will always return null
            if (parameterTypes.get(i).equals(UNKNOWN) && !argumentDefinitions.get(i).isNullable()) {
                return true;
            }
        }
        return false;
    }

    public FunctionMetadata getFunctionMetadata(ResolvedFunction resolvedFunction)
    {
        FunctionMetadata functionMetadata = getSpecializedFunctionKey(resolvedFunction).getFunction().getFunctionMetadata();

        // specialize function metadata to resolvedFunction
        List<FunctionArgumentDefinition> argumentDefinitions;
        if (functionMetadata.getSignature().isVariableArity()) {
            List<FunctionArgumentDefinition> fixedArguments = functionMetadata.getArgumentDefinitions().subList(0, functionMetadata.getArgumentDefinitions().size() - 1);
            int variableArgumentCount = resolvedFunction.getSignature().getArgumentTypes().size() - fixedArguments.size();
            argumentDefinitions = ImmutableList.<FunctionArgumentDefinition>builder()
                    .addAll(fixedArguments)
                    .addAll(nCopies(variableArgumentCount, functionMetadata.getArgumentDefinitions().get(functionMetadata.getArgumentDefinitions().size() - 1)))
                    .build();
        }
        else {
            argumentDefinitions = functionMetadata.getArgumentDefinitions();
        }
        return new FunctionMetadata(
                functionMetadata.getFunctionId(),
                resolvedFunction.getSignature(),
                functionMetadata.isNullable(),
                argumentDefinitions,
                functionMetadata.isHidden(),
                functionMetadata.isDeterministic(),
                functionMetadata.getDescription(),
                functionMetadata.getKind());
    }

    public AggregationFunctionMetadata getAggregationFunctionMetadata(ResolvedFunction resolvedFunction)
    {
        SqlFunction function = getSpecializedFunctionKey(resolvedFunction).getFunction();
        checkArgument(function instanceof SqlAggregationFunction, "%s is not an aggregation function", resolvedFunction);

        SqlAggregationFunction aggregationFunction = (SqlAggregationFunction) function;
        if (!aggregationFunction.isDecomposable()) {
            return new AggregationFunctionMetadata(aggregationFunction.isOrderSensitive(), Optional.empty());
        }

        InternalAggregationFunction implementation = getAggregateFunctionImplementation(resolvedFunction);
        return new AggregationFunctionMetadata(aggregationFunction.isOrderSensitive(), Optional.of(implementation.getIntermediateType().getTypeSignature()));
    }

    public WindowFunctionSupplier getWindowFunctionImplementation(ResolvedFunction resolvedFunction)
    {
        try {
            return specializedWindowCache.getUnchecked(getSpecializedFunctionKey(resolvedFunction));
        }
        catch (UncheckedExecutionException e) {
            throwIfInstanceOf(e.getCause(), PrestoException.class);
            throw e;
        }
    }

    public InternalAggregationFunction getAggregateFunctionImplementation(ResolvedFunction resolvedFunction)
    {
        try {
            return specializedAggregationCache.getUnchecked(getSpecializedFunctionKey(resolvedFunction));
        }
        catch (UncheckedExecutionException e) {
            throwIfInstanceOf(e.getCause(), PrestoException.class);
            throw e;
        }
    }

    public ScalarFunctionImplementation getScalarFunctionImplementation(ResolvedFunction resolvedFunction)
    {
        try {
            return specializedScalarCache.getUnchecked(getSpecializedFunctionKey(resolvedFunction));
        }
        catch (UncheckedExecutionException e) {
            throwIfInstanceOf(e.getCause(), PrestoException.class);
            throw e;
        }
    }

    private SpecializedFunctionKey getSpecializedFunctionKey(ResolvedFunction resolvedFunction)
    {
        SqlFunction function = functions.get(resolvedFunction.getFunctionId());
        Signature signature = resolvedFunction.getSignature();
        BoundVariables boundVariables = new SignatureBinder(metadata, function.getFunctionMetadata().getSignature(), false)
                .bindVariables(fromTypeSignatures(signature.getArgumentTypes()), signature.getReturnType())
                .orElseThrow(() -> new IllegalArgumentException("Could not extract bound variables"));
        return new SpecializedFunctionKey(
                function,
                boundVariables,
                signature.getArgumentTypes().size());
    }

    public boolean canResolveOperator(OperatorType operatorType, Type returnType, List<? extends Type> argumentTypes)
    {
        try {
            Signature signature = new Signature(
                    mangleOperatorName(operatorType),
                    returnType.getTypeSignature(),
                    argumentTypes.stream().map(Type::getTypeSignature).collect(toImmutableList()));
            // TODO: this is hacky, but until the magic literal and row field reference hacks are cleaned up it's difficult to implement this.
            getScalarFunctionImplementation(new ResolvedFunction(signature, toFunctionId(signature)));
            return true;
        }
        catch (PrestoException e) {
            if (e.getErrorCode().getCode() == FUNCTION_IMPLEMENTATION_MISSING.toErrorCode().getCode()) {
                return false;
            }
            throw e;
        }
    }

    public ResolvedFunction resolveOperator(OperatorType operatorType, List<? extends Type> argumentTypes)
            throws OperatorNotFoundException
    {
        try {
            return resolveFunction(QualifiedName.of(mangleOperatorName(operatorType)), fromTypes(argumentTypes));
        }
        catch (PrestoException e) {
            if (e.getErrorCode().getCode() == FUNCTION_NOT_FOUND.toErrorCode().getCode()) {
                throw new OperatorNotFoundException(operatorType, argumentTypes);
            }
            else {
                throw e;
            }
        }
    }

    public ResolvedFunction getCoercion(OperatorType operatorType, Type fromType, Type toType)
    {
        checkArgument(operatorType == OperatorType.CAST || operatorType == OperatorType.SATURATED_FLOOR_CAST);
        try {
            Signature signature = new Signature(mangleOperatorName(operatorType), toType.getTypeSignature(), ImmutableList.of(fromType.getTypeSignature()));
            ResolvedFunction resolvedFunction = resolveCoercion(signature);
            getScalarFunctionImplementation(resolvedFunction);
            return resolvedFunction;
        }
        catch (PrestoException e) {
            if (e.getErrorCode().getCode() == FUNCTION_IMPLEMENTATION_MISSING.toErrorCode().getCode()) {
                throw new OperatorNotFoundException(operatorType, ImmutableList.of(fromType), toType.getTypeSignature());
            }
            throw e;
        }
    }

    public ResolvedFunction getCoercion(QualifiedName name, Type fromType, Type toType)
    {
        ResolvedFunction resolvedFunction = resolveCoercion(new Signature(name.getSuffix(), toType.getTypeSignature(), ImmutableList.of(fromType.getTypeSignature())));
        getScalarFunctionImplementation(resolvedFunction);
        return resolvedFunction;
    }

    private ResolvedFunction resolveCoercion(Signature signature)
    {
        Collection<FunctionMetadata> allCandidates = functions.get(QualifiedName.of(signature.getName()));

        List<TypeSignatureProvider> argumentTypeSignatureProviders = fromTypeSignatures(signature.getArgumentTypes());

        List<FunctionMetadata> exactCandidates = allCandidates.stream()
                .filter(function -> possibleExactCastMatch(signature, function.getSignature()))
                .collect(Collectors.toList());
        for (FunctionMetadata candidate : exactCandidates) {
            Optional<BoundVariables> boundVariables = new SignatureBinder(metadata, candidate.getSignature(), false)
                    .bindVariables(argumentTypeSignatureProviders, signature.getReturnType());
            if (boundVariables.isPresent()) {
                return new ResolvedFunction(signature, candidate.getFunctionId());
            }
        }

        // only consider generic genericCandidates
        List<FunctionMetadata> genericCandidates = allCandidates.stream()
                .filter(function -> !function.getSignature().getTypeVariableConstraints().isEmpty())
                .collect(Collectors.toList());
        for (FunctionMetadata candidate : genericCandidates) {
            Optional<BoundVariables> boundVariables = new SignatureBinder(metadata, candidate.getSignature(), false)
                    .bindVariables(argumentTypeSignatureProviders, signature.getReturnType());
            if (boundVariables.isPresent()) {
                return new ResolvedFunction(signature, candidate.getFunctionId());
            }
        }

        throw new PrestoException(FUNCTION_IMPLEMENTATION_MISSING, format("%s not found", signature));
    }

    private static boolean possibleExactCastMatch(Signature signature, Signature declaredSignature)
    {
        if (!declaredSignature.getTypeVariableConstraints().isEmpty()) {
            return false;
        }
        if (!declaredSignature.getReturnType().getBase().equalsIgnoreCase(signature.getReturnType().getBase())) {
            return false;
        }
        if (!declaredSignature.getArgumentTypes().get(0).getBase().equalsIgnoreCase(signature.getArgumentTypes().get(0).getBase())) {
            return false;
        }
        return true;
    }

    private Optional<List<Type>> toTypes(List<TypeSignatureProvider> typeSignatureProviders)
    {
        ImmutableList.Builder<Type> resultBuilder = ImmutableList.builder();
        for (TypeSignatureProvider typeSignatureProvider : typeSignatureProviders) {
            if (typeSignatureProvider.hasDependency()) {
                return Optional.empty();
            }
            resultBuilder.add(metadata.getType(typeSignatureProvider.getTypeSignature()));
        }
        return Optional.of(resultBuilder.build());
    }

    /**
     * One method is more specific than another if invocation handled by the first method could be passed on to the other one
     */
    private boolean isMoreSpecificThan(ApplicableFunction left, ApplicableFunction right)
    {
        List<TypeSignatureProvider> resolvedTypes = fromTypeSignatures(left.getBoundSignature().getArgumentTypes());
        Optional<BoundVariables> boundVariables = new SignatureBinder(metadata, right.getDeclaredSignature(), true)
                .bindVariables(resolvedTypes);
        return boundVariables.isPresent();
    }

    private static class FunctionMap
    {
        private final Map<FunctionId, SqlFunction> functions;
        private final Multimap<QualifiedName, FunctionMetadata> functionsByName;

        public FunctionMap()
        {
            functions = ImmutableMap.of();
            functionsByName = ImmutableListMultimap.of();
        }

        public FunctionMap(FunctionMap map, Collection<? extends SqlFunction> functions)
        {
            this.functions = ImmutableMap.<FunctionId, SqlFunction>builder()
                    .putAll(map.functions)
                    .putAll(Maps.uniqueIndex(functions, function -> function.getFunctionMetadata().getFunctionId()))
                    .build();

            ImmutableListMultimap.Builder<QualifiedName, FunctionMetadata> functionsByName = ImmutableListMultimap.<QualifiedName, FunctionMetadata>builder()
                    .putAll(map.functionsByName);
            functions.stream()
                    .map(SqlFunction::getFunctionMetadata)
                    .forEach(functionMetadata -> functionsByName.put(QualifiedName.of(functionMetadata.getSignature().getName()), functionMetadata));
            this.functionsByName = functionsByName.build();

            // Make sure all functions with the same name are aggregations or none of them are
            for (Map.Entry<QualifiedName, Collection<FunctionMetadata>> entry : this.functionsByName.asMap().entrySet()) {
                Collection<FunctionMetadata> values = entry.getValue();
                long aggregations = values.stream()
                        .map(FunctionMetadata::getKind)
                        .filter(kind -> kind == AGGREGATE)
                        .count();
                checkState(aggregations == 0 || aggregations == values.size(), "'%s' is both an aggregation and a scalar function", entry.getKey());
            }
        }

        public List<FunctionMetadata> list()
        {
            return ImmutableList.copyOf(functionsByName.values());
        }

        public Collection<FunctionMetadata> get(QualifiedName name)
        {
            return functionsByName.get(name);
        }

        public SqlFunction get(FunctionId functionId)
        {
            SqlFunction sqlFunction = functions.get(functionId);
            checkArgument(sqlFunction != null, "Unknown function implementation: " + functionId);
            return sqlFunction;
        }
    }

    private static class ApplicableFunction
    {
        private final FunctionId functionId;
        private final Signature declaredSignature;
        private final Signature boundSignature;

        private ApplicableFunction(FunctionId functionId, Signature declaredSignature, Signature boundSignature)
        {
            this.functionId = functionId;
            this.declaredSignature = declaredSignature;
            this.boundSignature = boundSignature;
        }

        public Signature getDeclaredSignature()
        {
            return declaredSignature;
        }

        public Signature getBoundSignature()
        {
            return boundSignature;
        }

        public ResolvedFunction getResolvedFunction()
        {
            return new ResolvedFunction(boundSignature, functionId);
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("declaredSignature", declaredSignature)
                    .add("boundSignature", boundSignature)
                    .toString();
        }
    }
}
