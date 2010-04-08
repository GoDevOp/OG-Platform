/**
 * Copyright (C) 2009 - 2010 by OpenGamma Inc.
 *
 * Please see distribution for license.
 */
package com.opengamma.financial.riskfactor;

import java.util.HashMap;
import java.util.Map;

import com.opengamma.financial.greeks.FirstOrder;
import com.opengamma.financial.greeks.Greek;
import com.opengamma.financial.greeks.GreekResult;
import com.opengamma.financial.greeks.GreekResultCollection;
import com.opengamma.financial.greeks.GreekVisitor;
import com.opengamma.financial.greeks.MixedSecondOrder;
import com.opengamma.financial.greeks.MultipleGreekResult;
import com.opengamma.financial.greeks.Order;
import com.opengamma.financial.greeks.SecondOrder;
import com.opengamma.financial.greeks.SingleGreekResult;
import com.opengamma.financial.pnl.TradeData;
import com.opengamma.financial.pnl.Underlying;
import com.opengamma.financial.sensitivity.Sensitivity;
import com.opengamma.math.function.Function1D;

/**
 * @author emcleod
 *
 */
public class GreekToValueGreekConverter extends Function1D<GreekDataBundle, Map<Sensitivity, RiskFactorResult<?>>> {
  private final GreekVisitor<Sensitivity> _visitor = new GreekToValueGreekVisitor();

  /*
   * (non-Javadoc)
   * 
   * @see com.opengamma.math.function.Function1D#evaluate(java.lang.Object)
   */
  @Override
  public Map<Sensitivity, RiskFactorResult<?>> evaluate(final GreekDataBundle data) {
    if (data == null)
      throw new IllegalArgumentException("Risk factor data bundle was null");
    final GreekResultCollection greeks = data.getAllGreekValues();
    final Map<Sensitivity, RiskFactorResult<?>> riskFactors = new HashMap<Sensitivity, RiskFactorResult<?>>();
    Map<String, Double> multipleGreekResult;
    Map<Object, Double> riskFactorResultMap;
    Greek key;
    GreekResult<?> value;
    for (final Map.Entry<Greek, GreekResult<?>> entry : greeks.entrySet()) {
      key = entry.getKey();
      value = entry.getValue();
      if (entry.getValue() instanceof SingleGreekResult) {
        riskFactors.put(key.accept(_visitor), new SingleRiskFactorResult(getValueGreek(key, data, (Double) value.getResult())));
      } else if (entry.getValue() instanceof MultipleGreekResult) {
        multipleGreekResult = ((MultipleGreekResult) value).getResult();
        riskFactorResultMap = new HashMap<Object, Double>();
        for (final Map.Entry<String, Double> e : multipleGreekResult.entrySet()) {
          riskFactorResultMap.put(e.getKey(), getValueGreek(key, data, e.getValue()));
        }
        riskFactors.put(key.accept(_visitor), new MultipleRiskFactorResult(riskFactorResultMap));
      } else {
        throw new IllegalArgumentException("Can only handle SingleGreekResult and MultipleGreekResult");
      }
    }
    return riskFactors;
  }

  // TODO handle theta separately?
  Double getValueGreek(final Greek greek, final GreekDataBundle data, final Double greekValue) {
    final Order order = greek.getOrder();
    final Map<Object, Double> underlyings = data.getAllUnderlyingDataForGreek(greek);
    if (order instanceof FirstOrder) {
      final Underlying underlying = ((FirstOrder) order).getVariable();
      return greekValue * underlyings.get(underlying) * underlyings.get(TradeData.POINT_VALUE) * underlyings.get(TradeData.NUMBER_OF_CONTRACTS);
    }
    if (order instanceof SecondOrder) {
      final Underlying underlying = ((SecondOrder) order).getVariable();
      final double x = underlyings.get(underlying);
      return greekValue * x * x * underlyings.get(TradeData.POINT_VALUE) * underlyings.get(TradeData.NUMBER_OF_CONTRACTS);
    }
    if (order instanceof MixedSecondOrder) {
      final MixedSecondOrder mixedFirst = ((MixedSecondOrder) order);
      return greekValue * underlyings.get(mixedFirst.getFirstVariable().getVariable()) * underlyings.get(mixedFirst.getSecondVariable().getVariable())
          * underlyings.get(TradeData.POINT_VALUE) * underlyings.get(TradeData.NUMBER_OF_CONTRACTS);
    }
    throw new IllegalArgumentException("Can currently only handle first-, mixed-second- and second-order greeks");// TODO
  }

}
