/**
 * Copyright (C) 2013 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.sesame.fxforward;

import com.opengamma.financial.analytics.DoubleLabelledMatrix1D;
import com.opengamma.financial.security.fx.FXForwardSecurity;
import com.opengamma.sesame.FunctionResult;
import com.opengamma.sesame.example.OutputNames;
import com.opengamma.sesame.function.Output;

/**
 * Generates yield curve node sensitivities for an FX Forward security
 */
public interface FxForwardYieldCurveNodeSensitivitiesFunction {

  @Output(OutputNames.YIELD_CURVE_NODE_SENSITIVITIES)
  FunctionResult<DoubleLabelledMatrix1D> calculateYieldCurveNodeSensitivities(FXForwardSecurity security);
}
