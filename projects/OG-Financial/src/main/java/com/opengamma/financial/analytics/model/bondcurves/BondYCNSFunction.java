/**
 * Copyright (C) 2013 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.financial.analytics.model.bondcurves;

import static com.opengamma.engine.value.ValuePropertyNames.CURVE;
import static com.opengamma.engine.value.ValuePropertyNames.CURVE_EXPOSURES;
import static com.opengamma.engine.value.ValueRequirementNames.BLOCK_CURVE_SENSITIVITIES;
import static com.opengamma.engine.value.ValueRequirementNames.CURVE_DEFINITION;
import static com.opengamma.engine.value.ValueRequirementNames.YIELD_CURVE_NODE_SENSITIVITIES;
import static com.opengamma.financial.analytics.model.curve.CurveCalculationPropertyNamesAndValues.PROPERTY_CURVE_TYPE;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opengamma.analytics.financial.provider.description.interestrate.ParameterIssuerProviderInterface;
import com.opengamma.analytics.financial.provider.sensitivity.multicurve.MultipleCurrencyMulticurveSensitivity;
import com.opengamma.analytics.financial.provider.sensitivity.multicurve.MultipleCurrencyParameterSensitivity;
import com.opengamma.analytics.math.matrix.DoubleMatrix1D;
import com.opengamma.engine.ComputationTarget;
import com.opengamma.engine.ComputationTargetSpecification;
import com.opengamma.engine.function.FunctionCompilationContext;
import com.opengamma.engine.function.FunctionExecutionContext;
import com.opengamma.engine.function.FunctionInputs;
import com.opengamma.engine.value.ComputedValue;
import com.opengamma.engine.value.ValueProperties;
import com.opengamma.engine.value.ValueProperties.Builder;
import com.opengamma.engine.value.ValueRequirement;
import com.opengamma.engine.value.ValueRequirementNames;
import com.opengamma.engine.value.ValueSpecification;
import com.opengamma.util.async.AsynchronousExecution;
import com.opengamma.util.money.Currency;
import com.opengamma.util.tuple.Pair;

/**
 * Calculates the node sensitivities to the market quotes of a bond or bond future for all curves
 * to which the instruments are sensitive.
 */
public class BondYCNSFunction extends BondFromCurvesFunction<ParameterIssuerProviderInterface, MultipleCurrencyMulticurveSensitivity> {
  /** The logger */
  private static final Logger s_logger = LoggerFactory.getLogger(BondYCNSFunction.class);

  /**
   * Sets the value requirement name to {@link ValueRequirementNames#YIELD_CURVE_NODE_SENSITIVITIES} and
   * sets the calculator to null.
   */
  public BondYCNSFunction() {
    super(YIELD_CURVE_NODE_SENSITIVITIES, null);
  }

  @Override
  public Set<ComputedValue> execute(final FunctionExecutionContext context, final FunctionInputs inputs, final ComputationTarget target,
      final Set<ValueRequirement> desiredValues) throws AsynchronousExecution {
    final MultipleCurrencyParameterSensitivity sensitivities = (MultipleCurrencyParameterSensitivity) inputs.getValue(BLOCK_CURVE_SENSITIVITIES);
    final ValueRequirement desiredValue = desiredValues.iterator().next();
    final ValueProperties properties = desiredValue.getConstraints();
    final String desiredCurveName = desiredValue.getConstraint(CURVE);
    final Map<Pair<String, Currency>, DoubleMatrix1D> entries = sensitivities.getSensitivities();
    final Set<ComputedValue> results = new HashSet<>();
    boolean curveNameFound = false;
    final Builder noCurveProperties = properties.copy()
        .withoutAny(CURVE);
    for (final Map.Entry<Pair<String, Currency>, DoubleMatrix1D> entry : entries.entrySet()) {
      final String curveName = entry.getKey().getFirst();
      if (desiredCurveName.equals(curveName)) {
        curveNameFound = true;
      }
      final ValueProperties curveSpecificProperties = noCurveProperties
          .with(CURVE, curveName)
          .get();
      final ValueSpecification spec = new ValueSpecification(YIELD_CURVE_NODE_SENSITIVITIES, target.toSpecification(), curveSpecificProperties);
      results.add(new ComputedValue(spec, entry.getValue()));
    }
    if (!curveNameFound) {
      s_logger.info("Could not get sensitivities to " + desiredCurveName + " for " + target.getName());
      return Collections.emptySet();
    }
    return results;
  }

  @Override
  public Set<ValueRequirement> getRequirements(final FunctionCompilationContext context, final ComputationTarget target, final ValueRequirement desiredValue) {
    final ValueProperties constraints = desiredValue.getConstraints();
    final Set<String> curveNames = constraints.getValues(CURVE);
    if (curveNames == null || curveNames.size() != 1) {
      return null;
    }
    final Set<String> curveExposureConfigs = constraints.getValues(CURVE_EXPOSURES);
    if (curveExposureConfigs == null) {
      return null;
    }
    final Set<String> curveType = constraints.getValues(PROPERTY_CURVE_TYPE);
    if (curveType == null) {
      return null;
    }
    if (super.getRequirements(context, target, desiredValue) == null) {
      return null;
    }
    final Set<ValueRequirement> requirements = new HashSet<>();
    final ValueProperties curveProperties = ValueProperties
        .with(CURVE, curveNames)
        .get();
    final ValueProperties properties = ValueProperties
        .with(PROPERTY_CURVE_TYPE, curveType)
        .with(CURVE_EXPOSURES, curveExposureConfigs)
        .get();
    requirements.add(new ValueRequirement(CURVE_DEFINITION, ComputationTargetSpecification.NULL, curveProperties));
    requirements.add(new ValueRequirement(BLOCK_CURVE_SENSITIVITIES, target.toSpecification(), properties));
    return requirements;
  }

  @Override
  protected ValueProperties.Builder getResultProperties(final ComputationTarget target) {
    return super.getResultProperties(target)
        .withAny(CURVE);
  }

}
