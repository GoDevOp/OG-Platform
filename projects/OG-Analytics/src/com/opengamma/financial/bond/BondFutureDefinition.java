/**
 * Copyright (C) 2009 - 2010 by OpenGamma Inc.
 *
 * Please see distribution for license.
 */
package com.opengamma.financial.bond;

import java.util.Arrays;

import javax.time.calendar.LocalDate;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.Validate;

import com.opengamma.financial.interestrate.bond.definition.BondForward;
import com.opengamma.financial.interestrate.future.definition.BondFuture;

/**
 * 
 */
public class BondFutureDefinition implements InterestRateDerivativeProvider<BondFuture> {
  private final BondDefinition[] _deliverableBonds;
  private final double[] _conversionFactors;
  private final BondConvention _convention;
  private final LocalDate _deliveryDate;

  public BondFutureDefinition(final BondDefinition[] deliverableBonds, final double[] conversionFactors, final BondConvention convention, final LocalDate deliveryDate) {
    Validate.noNullElements(deliverableBonds, "deliverable bonds");
    Validate.notNull(conversionFactors, "conversion factor");
    Validate.isTrue(deliverableBonds.length == conversionFactors.length, "each deliverable bond must have a conversion factor");
    Validate.notNull(convention, "convention");
    Validate.notNull(deliveryDate, "delivery date");
    _deliverableBonds = deliverableBonds;
    _conversionFactors = conversionFactors;
    _convention = convention;
    _deliveryDate = deliveryDate;
  }

  public BondDefinition[] getDeliverableBonds() {
    return _deliverableBonds;
  }

  public double[] getConversionFactors() {
    return _conversionFactors;
  }

  public BondConvention getConvention() {
    return _convention;
  }

  public LocalDate getDeliveryDate() {
    return _deliveryDate;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + _convention.hashCode();
    result = prime * result + Arrays.hashCode(_conversionFactors);
    result = prime * result + Arrays.hashCode(_deliverableBonds);
    result = prime * result + _deliveryDate.hashCode();
    return result;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final BondFutureDefinition other = (BondFutureDefinition) obj;
    if (!Arrays.equals(_conversionFactors, other._conversionFactors)) {
      return false;
    }
    if (!ObjectUtils.equals(_deliveryDate, other._deliveryDate)) {
      return false;
    }
    if (!ObjectUtils.equals(_convention, other._convention)) {
      return false;
    }
    return Arrays.equals(_deliverableBonds, other._deliverableBonds);
  }

  @Override
  public BondFuture toDerivative(final LocalDate date, final String... yieldCurveNames) {
    Validate.notNull(date, "date");
    Validate.notNull(yieldCurveNames, "yield curve name(s)");
    final int n = _deliverableBonds.length;
    final BondForward[] bondForwards = new BondForward[n];
    for (int i = 0; i < n; i++) {
      bondForwards[i] = new BondForwardDefinition(_deliverableBonds[i], _deliveryDate, _convention).toDerivative(date, yieldCurveNames);
    }
    return new BondFuture(bondForwards, _conversionFactors);
  }

}
